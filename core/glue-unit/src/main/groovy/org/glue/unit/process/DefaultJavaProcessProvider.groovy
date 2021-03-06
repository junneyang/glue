package org.glue.unit.process

import org.apache.log4j.Logger
import org.glue.unit.om.Provider

/**
 * Is a helper class for a JavaProcess provider
 *
 */

class DefaultJavaProcessProvider extends Provider<JavaProcess>{

	private static final Logger LOG = Logger.getLogger(DefaultJavaProcessProvider.class)
	
	String mainClass
	Set<String> classpath = []
	Set<String> javaOpts = []
	File workingDirectory
	
	/**
	 * the map is used to push through any extra or future config that can only be decided during execution time.
	 */
	Map<String, String> config;
	
	public DefaultJavaProcessProvider(){
		
	}
	
	public DefaultJavaProcessProvider(Map<String, String> config){
		this.config = config;
	}
	
	/**
	 * Add the current java.class.path to the classpath variable, plus the paths:
	 * <ul> 
	 *  <li>target/classes</li>
	 *  <li>target/test-classes</li>
	 * </ul>
	 */
	void addCurrentClassPath(){
		String sysClassPath = System.getProperty("java.class.path")
		
		
		sysClassPath.split(File.pathSeparator).each { classpath << it }
		//we add the target/classes and test-classes to provide default
		//maven testing support
		classpath << new File("target/classes/").absolutePath
		classpath << new File("target/test-classes/").absolutePath
		
	}
	
	JavaProcess get(){
		return get(null)
	}
	
	/**
	 * Returns a new instance of JavaProcess with the same configuration parameters<br/>
	 * that the DefaultJavaProcessProvider has.
	 */
	JavaProcess get(Object... objs){
		
		def localJavaOpts = javaOpts;
		def localClasspath = classpath
		
		println "Objs: $objs"
		if(objs && objs.length > 0 && objs[0]){
			String name = objs[0].toString()
			
			if(config.containsKey("${name}_processJavaOpts".intern())){
				localJavaOpts = config."${name}_processJavaOpts"
				
				LOG.info("Using javaOpts specified for $name")
			}
			
		    if(config.containsKey("${name}_processClassPath".intern())){
				localClasspath = config."${name}_processClassPath"
				
				LOG.info("Using classpath specified for $name")
		    }
		}
		
		def process = new JavaProcess(
			workingDirectory:workingDirectory,
			mainClass:mainClass, 
			javaOpts:localJavaOpts)	
		
		localClasspath?.each { item ->
			
			File file = new File(item)
			if(file.isDirectory()){
				process.addJars(file)
				process.classpath << item
			}else{
				process.classpath << item
			}
			
		}

		return process
	}
	
}
