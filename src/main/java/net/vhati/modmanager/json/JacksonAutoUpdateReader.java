package net.vhati.modmanager.json;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ComparableVersion;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class JacksonAutoUpdateReader {

	private static final Logger log = LogManager.getLogger(JacksonAutoUpdateReader.class);


	public static AutoUpdateInfo parse( File jsonFile ) {
		AutoUpdateInfo aui = new AutoUpdateInfo();

		Exception exception = null;
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure( JsonParser.Feature.ALLOW_SINGLE_QUOTES, true );
			mapper.setVisibility( PropertyAccessor.FIELD, Visibility.ANY );

			JsonNode rootNode = mapper.readTree( jsonFile );
			JsonNode historiesNode = rootNode.get( "history_versions" );
			JsonNode historyNode = historiesNode.get( "1" );

			JsonNode latestNode = historyNode.get( "latest" );
			aui.setLatestVersion( new ComparableVersion( latestNode.get( "version" ).textValue() ) );

			Iterator<Map.Entry<String,JsonNode>> fieldIt = latestNode.get( "urls" ).fields();
			while ( fieldIt.hasNext() ) {
				Map.Entry<String,JsonNode> entry = fieldIt.next();
				aui.putLatestURL( entry.getKey(), entry.getValue().textValue() );
			}

			aui.setNotice( latestNode.get( "notice" ).textValue() );

			JsonNode changelogNode = historyNode.get( "changelog" );

			for ( JsonNode releaseNode : changelogNode ) {
				String releaseVersion = releaseNode.get( "version" ).textValue();

				List<String> changeList = new ArrayList<String>( releaseNode.get( "changes" ).size() );
				for ( JsonNode changeNode : releaseNode.get( "changes" ) ) {
					changeList.add( changeNode.textValue() );
				}
				aui.putChanges( new ComparableVersion( releaseVersion ), changeList );
			}
		}
		catch ( JsonProcessingException e ) {
			exception = e;
		}
		catch ( IOException e ) {
			exception = e;
		}
		if ( exception != null ) {
			log.error( exception );
			return null;
		}

		return aui;
	}
}
