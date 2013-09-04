package net.vhati.modmanager.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;


public class SlipstreamConfig {

	private Properties config;
	private File configFile;


	public SlipstreamConfig( Properties config, File configFile ) {
		this.config = config;
		this.configFile = configFile;
	}


	public Properties getConfig() { return config; }

	public File getConfigFile() { return configFile; }


	public Object setProperty( String key, String value ) {
		return config.setProperty( key, value );
	}

	public String getProperty( String key, String defaultValue ) {
		return config.getProperty( key, defaultValue );
	}

	public String getProperty( String key ) {
		return config.getProperty( key );
	}


	public void writeConfig() throws IOException {

		OutputStream out = null;
		try {
			out = new FileOutputStream( configFile );
			String configComments = "";
			configComments += "\n";
			configComments += " allow_zip - Sets whether to treat .zip files as .ftl files. Default: false.\n";
			configComments += " ftl_dats_path - The path to FTL's resources folder. If invalid, you'll be prompted.\n";
			configComments += " never_run_ftl - If true, there will be no offer to run FTL after patching. Default: false.\n";
			configComments += " update_catalog - If true, periodically download descriptions for the latest mods. If invalid, you'll be prompted.\n";
			configComments += " use_default_ui - If true, no attempt will be made to resemble a native GUI. Default: false.\n";
			configComments += " manager_geometry - Saved position/size/etc of the main window.\n";

			OutputStreamWriter writer = new OutputStreamWriter( out, "UTF-8" );
			config.store( writer, configComments );
			writer.flush();
		}
		finally {
			try {if ( out != null ) out.close();}
			catch ( IOException e ) {}
		}
	}
}