
package org.glue.trigger.persist.db

import java.sql.*

import org.glue.trigger.persist.TriggerStore2
import org.glue.unit.om.LazySequenceUtil
import org.streams.commons.zookeeper.ZConnection
import org.streams.commons.zookeeper.ZLock

import clojure.lang.PersistentVector;


/**
 * 
 * Implements the TriggerStore interface to save all trigger events to a RDBMS.<br/>
 * All events are saved through a JPA hibernate connection.<br/>
 * The entities  TriggersFileEntity and TriggersCheckPointEntity are used to represent the<br/> 
 * different elements of the database.<br/>
 *
 */

class DBTriggerStore2 extends TriggerStore2{

	private static final Object LOCK = new Object()

	String url;
	String uid;
	String pwd;

	ZLock zlock;

	void destroy(){
		shutdown()
	}

	private String createListReadyFilesSql(String unitName){
		"select fileid, path from unitfiles uf, hdfsfiles hf, unittriggers ut WHERE uf.status = 'ready' AND uf.fileid = hf.id AND ut.id = uf.unitid AND ut.unit = '${unitName}'"
	}
	
	/**
	 * List all files that where updated as READY by the trigger<br/>
	 * The closure is called with (entityName:String, filePath:String)<br/>
	 * The closure will be called within the scope of a database transaction.<br/>
	 * @param unitName
	 * @param closure
	 */
	public void listReadyFiles(String unitName, Closure closure, boolean lock = true){


		if(unitName == null){
			throw new NullPointerException("No unit name was specified")
		}

		Connection conn = DriverManager.getConnection(url, uid, pwd);
		Statement st = conn.createStatement();
		try{
			
			ResultSet rs = st.executeQuery(createListReadyFilesSql(unitName));

			if(rs.first()){
				while(true){

					closure(rs.getInt(1), rs.getString(2));
					if(!rs.next())
						break;	
				}
			}
		}finally{
			st.close();
			conn.close()
		}
	}
 
	/**
	 * Returns a Collection of Object Arrays [id:int, name:String]
	 */
	public Collection listReadyFiles(String unitName, boolean lock = true){
		
		def batchLen = 500
		
		def flist = { pos ->
			//query batch from the db
			Connection conn = DriverManager.getConnection(url, uid, pwd)
			Statement st = conn.createStatement()
			ResultSet rs = st.executeQuery(createListReadyFilesSql(unitName))
			rs.setFetchSize(batchLen)
			
			def list = [] as ArrayList
			
		    try{
				//move to position pos
				if( (pos == 0)? rs.first() : rs.absolute(pos as int) ){
					for(int i = 0; i < batchLen; i++){
				  	     list << PersistentVector.create(rs.getInt(1), rs.getString(2))
						 if(!rs.next())
						   break;
					}
				}
			}finally{
			    st.close()
				conn.close()
			}
			
			list
		}
		
		//the lazy sequence will call the flist for every batch
		return LazySequenceUtil.seq(flist, 0)
	}
	/**
	 * Tries to lock a trigger file. if the lock cannot be acquired false is returned
	 * @param unintName
	 * @param path
	 * @return
	 */
	private boolean zlock(String unitName, String path){
		return (!zlock)? true : zlock?.lock(unitName);
	}


	/**
	 * Marks a file as processed
	 * @param unitName
	 * @param fileId ids
	 */
	public void markFilesAsProcessed(String unitName, fileIds){

		if(unitName == null){
			throw new NullPointerException("No unit name was specified")
		}

		Connection conn = DriverManager.getConnection(url, uid, pwd);
		conn.setAutoCommit(false);
		Statement st = conn.prepareStatement("INSERT INTO unitfiles (unitid, fileid, status) SELECT id, ?, 'processed' from unittriggers where unit = '${unitName}' ON DUPLICATE KEY UPDATE status='processed'");
		try{

			for(fileId in fileIds){
				st.setInt(1, fileId);
				st.addBatch();
			}
			
			st.executeBatch();
			conn.commit();
		}catch(BatchUpdateException exc){
			conn.rollback();
			throw exc;
		}finally{
			st.close();
			conn.close()
		}
	}


	/**
	 * 
	 * If any error an Exception is thrown.<br/>
	 * e.g.<br/>
	 * <pre>
	 * className=' org.glue.trigger.persist.db'
	 isSingleton=false
	 config{
	 connection.username="glue"
	 connection.password="glue"
	 connection.driver_class="com.mysql.jdbc.Driver"
	 connection.url="jdbc:mysql://127.0.0.1:3306/glue"
	 }
	 * </pre>
	 */
	@Override
	public void init(ConfigObject config) {

		
			synchronized (LOCK){


				Properties properties = config.toProperties();

				Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass(properties.getProperty('connection.driver'));

				url = properties.getProperty('connection.url');
				uid = properties.getProperty('connection.username');
				pwd = properties.getProperty('connection.password');


				if(!zlock){
					if(config.zkhost){
						println "Using zookeeper locking"
						long timeout =  (config.zktimeout) ? Long.parseLong(config.zktimeout.toString()) : 10000L
						zlock = new ZLock(new ZConnection(config.zkhost.toString(), timeout),
						"/glue-dbtriggerstore/", timeout);
					}else{
						println "$config"
						println "No zookeeper locking is used"
					}
				}
			}
		
	}

	public void shutdown(){
		
	}
}
