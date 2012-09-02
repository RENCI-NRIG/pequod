package orca.pequod.main;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import orca.manage.IOrcaContainer;
import orca.manage.Orca;
import orca.manage.OrcaError;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ResultMng;

/** takes care of maintaining connections
 * to different containers
 * @author ibaldin
 *
 */
public class ConnectionCache {
	protected Map<String, ConnectionState> activeConnections;
	protected List<ActorMng> actors;
	protected String username, password;
	protected boolean inError = false;
	protected String lastError = null;
	
	static class ConnectionState {
		private static final String SOAP_URL_HEADER = "soap://";
		String url, username, password;
		IOrcaContainer proxy;
		boolean inError = false;
		OrcaError lastError = null;
		
		ConnectionState(String url, String u, String p) {
			this.url = url;
			this.username = u;
			this.password = p;
			
			try {
				proxy = Orca.connect(SOAP_URL_HEADER + url, username, password);
			} catch (Exception e) {
				inError = true;
				ResultMng rm = new ResultMng();
				rm.setMessage("Unable to establish connection: " + e.getMessage());
				lastError = new OrcaError(rm, null);
			} 
			
			if (proxy == null) {
				inError = true;
				ResultMng rm = new ResultMng();
				rm.setMessage("Unable to establish connection");
				lastError = new OrcaError(rm, null);
			}
			
			if ((proxy != null ) && (!proxy.isLogged())){
				inError = true;
				lastError = proxy.getLastError();
			}
		}		
	}
	
	private void checkConnectionErrorState(ConnectionState proxy) {
		
		if (proxy.inError) {
			inError = true;
			if (lastError == null) {
				lastError = proxy.lastError.toString();
			} else {
				lastError += "; " + proxy.lastError.toString();
			}
		}
	}
	
	private void getBasicContainerData(ConnectionState cs) {
		// get the names of actors
		
		if (!cs.inError) {
			actors.addAll(cs.proxy.getActors());
		}
	}
	/**
	 * Initialize connection cache with a list of container URLs, username
	 * and password (assumed common across containers)
	 * @param urls
	 * @param username
	 * @param password
	 */
	public ConnectionCache(List<String> urls, String username, String password) {
		this.username = username;
		this.password = password;
		this.actors = new LinkedList<ActorMng>();
		
		activeConnections = new HashMap<String, ConnectionState>();
		
		for (String url: urls) {
			ConnectionState proxy = new ConnectionState(url, username, password);
			checkConnectionErrorState(proxy);
			if (!proxy.inError) {
				getBasicContainerData(proxy);
			}
			activeConnections.put(url, proxy);
		}
	}
	
	/**
	 * Add a container with unique username and password
	 * @param url
	 * @param username
	 * @param password
	 */
	public void addContainer(String url, String username, String password) {
		if (url == null) {
			return;
		}

		if (activeConnections.containsKey(url))
			return;
		
		ConnectionState proxy = new ConnectionState(url, username, password);
		checkConnectionErrorState(proxy);
		if (!proxy.inError) {
			getBasicContainerData(proxy);
			activeConnections.put(url, proxy);
		}
	}
	
	/**
	 * Retry a container (if it is ok, does nothing)
	 * @param url
	 */
	public void retryContainer(String url) {
		if (url == null)
			return;
		
		ConnectionState proxy = new ConnectionState(url, username, password);
		checkConnectionErrorState(proxy);
		if (!proxy.inError)
			activeConnections.put(url, proxy);
	}
	
	/**
	 * Are we in error state?
	 * @return
	 */
	public boolean inError() {
		return inError;
	}
	
	/**
	 * What was the last error message?
	 * @return
	 */
	public String getLastError() {
		return lastError;
	}
	
	/**
	 * reset error state
	 */
	public void resetError() {
		inError = false;
		lastError = null;
	}
	
	/**
	 * Returns a copy of list of containers with active connections
	 * @return
	 */
	public Collection<String> getContainers() {
		return new LinkedList<String>(activeConnections.keySet());
	}
	
	/**
	 * Return a copy of collection of known active actors across containers
	 * @return
	 */
	public Collection<ActorMng> getActiveActors() {
		return new LinkedList<ActorMng>(actors);
	}
	
	/**
	 * Is the connection to this container in error?
	 * @param url
	 * @return
	 */
	public boolean isConnectionInError(String url) {
		if (activeConnections.containsKey(url)) {
			ConnectionState cs = activeConnections.get(url);
			return cs.inError;
		}
		return true;
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 */
	public OrcaError getConnectionError(String url) {
		if (activeConnections.containsKey(url)) {
			ConnectionState cs = activeConnections.get(url);
			return cs.lastError;
		}
		return null;
	}
	
	/** 
	 * Get the actual proxy for this container url
	 * @param url
	 * @return
	 */
	public IOrcaContainer getContainer(String url) {
		if (activeConnections.containsKey(url)) {
			ConnectionState cs = activeConnections.get(url);
			return cs.proxy;
		}
		return null;
	}
	
	/**
	 * Cleanly logout from all containers
	 */
	protected void finalize() {
		// logout from all containers
		for (String s: activeConnections.keySet()) {
			ConnectionState cs = activeConnections.get(s);
			if (cs.proxy != null)
				cs.proxy.logout();
		}
	}
}
