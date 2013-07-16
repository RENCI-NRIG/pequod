package orca.pequod.main;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import orca.manage.IOrcaActor;
import orca.manage.IOrcaAuthority;
import orca.manage.IOrcaBroker;
import orca.manage.IOrcaContainer;
import orca.manage.IOrcaServiceManager;
import orca.manage.Orca;
import orca.manage.OrcaError;
import orca.manage.beans.ActorMng;
import orca.manage.beans.ReservationMng;
import orca.manage.beans.ResultMng;
import orca.manage.beans.SliceMng;
import orca.shirako.common.SliceID;
import orca.util.ID;

import org.apache.log4j.Logger;

/** takes care of maintaining connections
 * to different containers
 * @author ibaldin
 *
 */
public class ConnectionCache {
	protected Map<String, ConnectionState> activeConnections;
	protected Map<String, ActorState> activeActors;
	protected Map<String, ID> seenSlices;
	protected String username, password;
	protected boolean inError = false;
	protected String lastError = null;
	
	// current settings
	protected List<String> currentContainers;
	protected List<String> currentActors;
	protected List<String> currentSliceIds;
	protected List<String> currentReservationIds;
	
	// last 'show' settings
	protected List<String> lastShowContainers;
	protected List<ActorMng> lastShowActors;
	protected List<SliceMng> lastShowSlices;
	protected List<ReservationMng> lastShowReservations;
	
	private Logger logger = null;
	
	static class ActorState {
		ActorMng actor;
		ConnectionState proxy;
		Constants.ActorType type;
		
		ActorState(ActorMng a, ConnectionState cs) {
			actor = a;
			proxy = cs;
			type = Constants.ActorType.getType(a.getType());
		}
	}
	
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
	
	private synchronized void checkConnectionErrorState(ConnectionState proxy) {
		
		if (proxy.inError) {
			inError = true;
			if (lastError == null) {
				lastError = proxy.lastError.toString();
			} else {
				lastError += "; " + proxy.lastError.toString();
			}
		}
	}
	
	private synchronized void getBasicContainerData(ConnectionState cs) {
		// get the names of actors
		
		if (!cs.inError) {
			for (ActorMng a: cs.proxy.getActorsFromDatabase()) {
				// insert under name and guid 
				ActorState as = new ActorState(a, cs);
				activeActors.put(a.getName(), as);
				activeActors.put(a.getID(), as);
			}
		}
	}
	/**
	 * Initialize connection cache with a list of container URLs, username
	 * and password (assumed common across containers)
	 * @param urls
	 * @param username
	 * @param password
	 */
	public ConnectionCache(List<String> urls, final String username, final String password, Logger l) {
		this.username = username;
		this.password = password;
		this.logger = l;
		this.activeActors = new HashMap<String, ActorState>();
		this.seenSlices = new HashMap<String, ID>();
		
		activeConnections = new HashMap<String, ConnectionState>();
		
		List<Thread> threads = new ArrayList<Thread>();
		
		for (final String url: urls) {
			logger.info("Connecting " + url);
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						connect(url, username, password);
					} catch (URISyntaxException e) {
						logger.error("Unable to connect to " + url + " due to " + e);
					}
				}
			});
			threads.add(t);
			t.start();
		}
		
		for (Thread t: threads) {
			try {
				t.join(5000);
			} catch (InterruptedException e) {
				logger.error("Thread was interrupted");
			}
		}
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
	 * Return a copy of collection of known active actors in a given container
	 * if url is null, then all containers
	 * @param url - url of the container
	 * @return
	 */
	public Collection<ActorMng> getActiveActors(String url) {
		Set<ActorMng> ret = new HashSet<ActorMng>();
		for(ActorState as: activeActors.values()) {
			if ((url != null) && (!as.proxy.url.equals(url))) 
				continue;
			ret.add(as.actor);
		}
		return ret;
	}
	
	/**
	 * get active actors by type
	 * @param t
	 * @param url
	 * @return
	 */
	public Collection<ActorMng> getActiveActors(Constants.ActorType t, String url) {
		Set<ActorMng> ret = new HashSet<ActorMng>();
		for(ActorState as: activeActors.values()) {
			if (!as.proxy.url.equals(url)) {
				continue;
			}
			if ((t == Constants.ActorType.ACTOR) ||
					(t == Constants.ActorType.UNKNOWN))
				ret.add(as.actor);
			else 
				if (as.type == t)
					ret.add(as.actor);
		}
		return ret;
	}
	
	/**
	 * Get actor by its name or GUID
	 * @param nameOrGuid
	 * @return
	 */
	public ActorMng getActor(String nameOrGuid) {
		return activeActors.get(nameOrGuid).actor;
	}
	
	/**
	 * Get IOrcaActor for this name or guid
	 * @param nameOrGuid
	 * @return
	 */
	public IOrcaActor getOrcaActor(String nameOrGuid) {
		ActorState as = activeActors.get(nameOrGuid);
		
		if (as == null)
			return null;
		
		if (as.proxy.inError)
			return null;

		return as.proxy.proxy.getActor(new ID(as.actor.getID()));
	}
	
	/**
	 * Get authority by name or guid (or null)
	 * @param nameOrGuid
	 */
	public IOrcaAuthority getAuthority(String nameOrGuid) {
		ActorState as = activeActors.get(nameOrGuid);
		
		if (as == null)
			return null;
		
		if (as.proxy.inError)
			return null;
		
		if (as.type != Constants.ActorType.AM)
			return null;

		return as.proxy.proxy.getAuthority(new ID(as.actor.getID()));
	}
	
	/**
	 * Get broker by name or guid (or null)
	 * @param nameOrGuid
	 * @return
	 */
	public IOrcaBroker getBroker(String nameOrGuid) {
		ActorState as = activeActors.get(nameOrGuid);
		
		if (as == null)
			return null;
		
		if (as.proxy.inError)
			return null;
		
		if (as.type != Constants.ActorType.BROKER)
			return null;
		
		return as.proxy.proxy.getBroker(new ID(as.actor.getID()));
	}
	
	/**
	 * Get service manager by name or guid (or null)
	 * @param nameOrGuid
	 * @return
	 */
	public IOrcaServiceManager getServiceManager(String nameOrGuid) {
		ActorState as = activeActors.get(nameOrGuid);
		
		if (as == null)
			return null;
		
		if (as.proxy.inError)
			return null;
		
		if (as.type != Constants.ActorType.SM)
			return null;
		
		return as.proxy.proxy.getServiceManager(new ID(as.actor.getID()));
	}
	
	/**
	 * Get URL of the container for this actor
	 * @param nameOrGuid
	 * @return
	 */
	public String getActorContainer(String nameOrGuid) {
		ActorState as = activeActors.get(nameOrGuid);
		
		if (as == null)
			return null;
		
		if (as.proxy == null)
			return null;
		
		return as.proxy.url;
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
	 * Getters setters for current (containers, slices, actors, reservations)
	 */
	
	public void setCurrentContainers(List<String> url) {
		currentContainers = url;
	}
	public void setCurrentContainersFromLastShow() {
		currentContainers = lastShowContainers;
	}
	public List<String> getCurrentContainers() {
		return currentContainers;
	}
	public void setCurrentActors(List<String> a) {
		currentActors = a;
	}
	public void setCurrentActorsFromLastShow() {
		if (lastShowActors == null) {
			currentActors = null;
			return;
		}
		currentActors = new LinkedList<String>();
		for (ActorMng am: lastShowActors) {
			currentActors.add(am.getName());
		}
	}
	public List<String> getCurrentActors() {
		return currentActors;
	}
	public void setCurrentSliceIds(List<String> slices) {
		currentSliceIds = slices;
	}
	public void setCurrentSliceIdsFromLastShow() {
		if (lastShowSlices == null) {
			currentSliceIds = null;
			return;
		}
		currentSliceIds = new LinkedList<String>();
		for (SliceMng sm: lastShowSlices) {
			currentSliceIds.add(sm.getSliceID());
		}
	}
	public List<String> getCurrentSliceIds() {
		return currentSliceIds;
	}
	public void setCurrentReservationIdsFromLastShow() {
		if (lastShowReservations == null) {
			currentReservationIds = null;
			return;
		}
		currentReservationIds = new LinkedList<String>();
		for (ReservationMng rm: lastShowReservations) {
			currentReservationIds.add(rm.getReservationID());
			
		}
	}
	public void setCurrentReservationIds(List<String> res) {
		currentReservationIds = res;
	}
	public List<String> getCurrentReservationIds() {
		return currentReservationIds;
	}
	
	/**
	 * Getters setters for last show (containers, slices, actors, reservations)
	 */
	public void setLastShowContainers(List<String> urls) {
		lastShowContainers = urls;
	}
	public List<String> getLastShowContainers() {
		return lastShowContainers;
	}
	public void setLastShowActors(List<ActorMng> a) {
		lastShowActors = a;
	}
	public List<ActorMng> getLastShowActors() {
		return lastShowActors;
	}
	/**
	 * Same as getLastShowActors, but returns IOrcaActors instead
	 * of ActorMng
	 * @return
	 */
	public List<IOrcaActor> getLastShowOrcaActors() {
		List<IOrcaActor> ret = new LinkedList<IOrcaActor>();
		for(ActorMng am: lastShowActors) {
			ret.add(getOrcaActor(am.getName()));
		}
		return ret;
	}
	public void setLastShowSlices(List<SliceMng> s) {
		lastShowSlices = s;
	}
	public List<SliceMng> getLastShowSlices() {
		return lastShowSlices;
	}
	public void setLastShowReservations(List<ReservationMng> r) {
		lastShowReservations = r;
	}
	public List<ReservationMng> getLastShowReservations() {
		return lastShowReservations;
	}
	
	/**
	 * Cache the results of all getSlices calls
	 * @param actor
	 * @return
	 */
	public List<SliceMng> getActorSlices(IOrcaActor actor) {
		if (actor == null)
			return null;
		
		List<SliceMng> ret = actor.getSlices();
		
		if (ret != null)
			for (SliceMng s: ret) {
				seenSlices.put(s.getName(), new SliceID(s.getSliceID()));
			}
		
		return ret;
	}
	
	/**
	 * Disconnect from all containers
	 */
	public void shutdown() {
		// logout from all containers
		Set<String> acts = new HashSet<String>(activeConnections.keySet());
		for (String s: acts) {
			shutdown(s);
		}
	}
	
	/**
	 * Disconnect from a specified container
	 * @param url
	 */
	public void shutdown(String url) {
		if (url == null)
			return;
		ConnectionState cs = activeConnections.get(url);
		if ((cs != null) && (cs.proxy != null)) {
			List<ActorMng> aa = cs.proxy.getActorsFromDatabase();
			if (aa != null) {
				for (ActorMng a: aa) 
					activeActors.remove(a.getName());
			}
			cs.proxy.logout();
			activeConnections.remove(url);
		}
	}
	
	/**
	 * Connect to a specified container
	 * @param url
	 * @param user
	 * @param password
	 */
	public void connect(final String url, final String user, final String password) throws URISyntaxException {
		if (url == null)
			return;
		
		String u = username;
		String p = password;
		String lurl = url;

		URI uri = new URI(lurl);
		if (uri.getUserInfo() != null) {
			u = uri.getUserInfo().split(":")[0];
			p = (uri.getUserInfo().split(":").length == 1 ? password : uri.getUserInfo().split(":")[1]);
			lurl = uri.getScheme() + "://" + uri.getHost(); 
			lurl += ":" + uri.getPort();
			lurl += uri.getPath();
		} 
		
		synchronized(this) {
			ConnectionState cs = activeConnections.get(url);
			if (cs != null)
				return;
		}
		
		if ((u == null) || (p == null)) 
			throw new URISyntaxException(lurl, "Username or password is not specified in URL nor globally");

		ConnectionState proxy = new ConnectionState(lurl, u, p);
		
		synchronized(this) {
			checkConnectionErrorState(proxy);
			if (!proxy.inError) {
				getBasicContainerData(proxy);
			}
			activeConnections.put(lurl, proxy);
		}
	}
	
	/**
	 * Cleanly logout from all containers
	 */
	protected void finalize() {
		shutdown();
	}
}
