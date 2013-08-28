package net.vhati.modmanager.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.SAXParseException;

import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Parent;
import org.jdom2.Text;
import org.jdom2.input.JDOMParseException;


/**
 * A scraper for malformed XML.
 *
 * Sloppiness:
 *   Any closing tag, regardless of its name, closes the parent tag.
 *   <!-- <!-- blah --> is valid (but the extra dashes will be discarded).
 *   --> can occur alone (discarded).
 *   An attribute name can start right after the quote from a prior value.
 *   Namespace prefixes for nodes and attributes are unique.
 *     (Each prefix will be used as the namespace's URI).
 *
 * Only use this as a last resort, after a real parser fails.
 */
public class SloppyXMLParser {

	private Pattern declPtn = Pattern.compile( "(\\s*)<[?]xml [^?]*[?]>" );
	private Pattern commentPtn = Pattern.compile( "(?s)(\\s*)<!--((?:.(?!-->))*.)-->" );
	private Pattern cdataPtn = Pattern.compile( "(?s)(\\s*)<!\\[CDATA\\[((?:.(?!\\]\\]>))*.)\\]\\]>" );
	private Pattern sTagPtn = Pattern.compile( "(\\s*)<(?:(\\w+):)?(\\w+)((?: [^>]+?)??)\\s*(/?)>" );
	private Pattern eTagPtn = Pattern.compile( "([^<]*)</\\s*([^>]+)>" );
	private Pattern endSpacePtn = Pattern.compile( "\\s+$" );
	private Pattern strayECommentPtn = Pattern.compile( "(\\s*)-->" );
	private Pattern strayCharsPtn = Pattern.compile( "(\\s*)[-.>,]" );

	private Pattern attrPtn = Pattern.compile( "\\s*(?:(\\w+):)?(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*')" );

	private List<Pattern> chunkPtns = new ArrayList<Pattern>();


	public SloppyXMLParser() {
		chunkPtns.add( declPtn );
		chunkPtns.add( commentPtn );
		chunkPtns.add( cdataPtn );
		chunkPtns.add( sTagPtn );
		chunkPtns.add( eTagPtn );
		chunkPtns.add( endSpacePtn );
		chunkPtns.add( strayECommentPtn );
		chunkPtns.add( strayCharsPtn );
	}


	public Document build( CharSequence s ) throws JDOMParseException {
		Document doc = new Document();
		Element rootNode = new Element( "wrapper" );
		doc.addContent( rootNode );

		Parent parentNode = rootNode;
		int sLen = s.length();
		int lastPos = -1;
		int pos = 0;
		String tmp = null;
		Matcher m = declPtn.matcher( s );

		while ( pos > lastPos && pos < sLen ) {
			m.region( pos, sLen );
			boolean matchedChunk = false;

			for ( Pattern chunkPtn : chunkPtns ) {
				m.usePattern( chunkPtn );
				if ( !m.lookingAt() ) continue;

				if ( chunkPtn == declPtn ) {
					// Don't care.
				}
				else if ( chunkPtn == commentPtn ) {
					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						parentNode.addContent( new Text( whitespace ) );

					tmp = m.group( 2 );
					tmp = tmp.replaceAll( "^-+|(?<=-)-+|-+$", "" );
					Comment commentNode = new Comment( tmp );
					parentNode.addContent( commentNode );
				}
				else if ( chunkPtn == cdataPtn ) {
					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						parentNode.addContent( new Text( whitespace ) );

					CDATA cdataNode = new CDATA( m.group(2) );
					parentNode.addContent( cdataNode );
				}
				else if ( chunkPtn == sTagPtn ) {
					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						parentNode.addContent( new Text( whitespace ) );

					String nodePrefix = m.group( 2 );  // Might be null.
					String nodeName = m.group( 3 );
					String attrString = m.group( 4 );
					boolean selfClosing = ( m.group( 5 ).length() > 0 );

					Element tagNode;
					if ( nodePrefix != null ) {
						Namespace nodeNS = Namespace.getNamespace( nodePrefix, nodePrefix );  // URI? *shrug*
						rootNode.addNamespaceDeclaration( nodeNS );
						tagNode = new Element( nodeName, nodeNS );
					} else {
						tagNode = new Element( nodeName );
					}

					if ( attrString.length() > 0 ) {
						Matcher am = attrPtn.matcher( attrString );
						while ( am.lookingAt() ) {
							String attrPrefix = am.group( 1 );  // Might be null.
							String attrName = am.group( 2 );
							String attrValue = am.group( 3 );
							attrValue = attrValue.substring( 1, attrValue.length()-1 );

							if ( attrPrefix != null ) {
								if ( attrPrefix.equals( "xmlns" ) ) {
									// This is a pseudo attribute declaring a namespace.
									// Move it to the root node.
									Namespace attrNS = Namespace.getNamespace( attrName, attrName );  // URI? *shrug*
									rootNode.addNamespaceDeclaration( attrNS );
								}
								else {
									Namespace attrNS = Namespace.getNamespace( attrPrefix, attrPrefix );  // URI? *shrug*
									rootNode.addNamespaceDeclaration( attrNS );
									tagNode.setAttribute( attrName, attrValue, attrNS );
								}
							} else {
								tagNode.setAttribute( attrName, attrValue );
							}
							am.region( am.end(), am.regionEnd() );
						}
						if ( am.regionStart() < attrString.length() ) {
							int[] lineAndCol = getLineAndCol( s, pos );
							int lineNum = lineAndCol[0];
							int colNum = lineAndCol[1];

							SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Strange attributes.", lineNum, colNum ), null, null, lineNum, colNum);
							throw new JDOMParseException( String.format( "Error on line %d: %s", lineNum, cause.getMessage() ), cause );
						}
					}

					parentNode.addContent( tagNode );
					if ( !selfClosing ) parentNode = tagNode;
				}
				else if ( chunkPtn == eTagPtn ) {
					String interimText = m.group( 1 );
					parentNode.addContent( new Text( interimText ) );
					parentNode = parentNode.getParent();
				}
				else if ( chunkPtn == endSpacePtn ) {
					// This is the end of the document.
				}
				else if ( chunkPtn == strayECommentPtn ) {
					// Stray end-comment bracket.

					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						parentNode.addContent( new Text( whitespace ) );
				}
				else if ( chunkPtn == strayCharsPtn ) {
					// Non-space junk between an end tag and a start tag.

					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						parentNode.addContent( new Text( whitespace ) );
				}

				matchedChunk = true;
				lastPos = pos;
				pos = m.end();
				break;
			}

			if ( !matchedChunk ) {
				int[] lineAndCol = getLineAndCol( s, pos );
				int lineNum = lineAndCol[0];
				int colNum = lineAndCol[1];

				SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Unexpected characters.", lineNum, colNum ), null, null, lineNum, colNum);
				throw new JDOMParseException( String.format( "Error on line %d: %s", lineNum, cause.getMessage() ), cause );
			}
		}

		return doc;
	}

	/**
	 * Returns lineNum and colNum for a position in text.
	 */
	private int[] getLineAndCol( CharSequence s, int pos ) {
		Matcher breakMatcher = Pattern.compile( "\n" ).matcher( s );
		breakMatcher.region( 0, pos+1 );
		int lastBreakPos = -1;
		int lineNum = 1;
		while ( breakMatcher.find() ) {
			lastBreakPos = breakMatcher.start();
			breakMatcher.region( breakMatcher.end(), breakMatcher.regionEnd() );
			lineNum++;
		}
		int colNum;
		if ( lastBreakPos == -1 )
			colNum = pos+1;
		else
			colNum = pos - lastBreakPos;

		return new int[] { lineNum, colNum };
	}
}
