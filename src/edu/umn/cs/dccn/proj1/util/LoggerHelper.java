/**
 * 
 */
package edu.umn.cs.dccn.proj1.util;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Helper class with methods related to the Logger
 * and its settings.
 * <p>
 * The default configuration file can be found in config/logback.xml.
 * Knowledge about <a href="http://logback.qos.ch/">Logback</a> is 
 * required to understand the configuration file.
 * 
 * @author Vivek Ranjan
 *
 */
public class LoggerHelper {
	
	
	/**
	 * Sets the specified class's logger's level as supplied by the user.
	 * By default the log level is set to INFO which is perfect for 
	 * production use. The various levels available are:
	 * 
	 * <ul>
	 * <li>INFO - Good for production environment. Displays important/necessary stuff.
	 * <li>DEBUG - Good for development and debugging purposes.
	 * <li>TRACE - Highly verbose and overwhelming. In depth knowledge of program required. Otherwise won't make sense.
	 * </ul>
	 * <p>
	 * DEBUG and TRACE are mostly used during development for debugging
	 * and keeping a track of what is going on. They may overwhelm the user
	 * if used in a production environment.
	 * 
	 * @param logLevel2 The level of logging to use. 
	 */
	@SuppressWarnings("rawtypes")
	public static void setLogLevel(String logLevel2, Class reqClass) {
		ch.qos.logback.classic.Logger log2=
				(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(LoggerHelper.class);
		log2.trace("setLogLevel: {},{}",logLevel2,reqClass);
		ch.qos.logback.classic.Logger log=(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(reqClass);
		boolean logLevelSet=true;
		if(logLevel2.equalsIgnoreCase("info")) {
			log.setLevel(Level.INFO);
		} else if(logLevel2.equalsIgnoreCase("debug")) {
			log.setLevel(Level.DEBUG);
		} else if(logLevel2.equalsIgnoreCase("trace")) {
			log.setLevel(Level.TRACE);
		} else {
			log2.error("Invalid log level provided. Defaulting to INFO");
			logLevelSet=false;
			setLogLevel("INFO", reqClass);
		}
		if(logLevelSet) {
			log2.debug("{}.logLevel:{}",reqClass,logLevel2);
		}
	}

}
