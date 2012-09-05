package orca.pequod.main;

public class Constants {

	public enum ActorType {
		AM(3, "am"),
		SM(1, "sm"),
		BROKER(2, "broker"),
		ACTOR(4, "actor"),
		UNKNOWN(5, "unknown");
		
		int type;
		String name;
		
		ActorType(int t, String n) {
			type = t;
			name = n;
		}
		public String getName() {
			return name;
		}
		
		public String getPluralName() {
			return name + "s";
		}
		
		public boolean amThisType(String o) {
			if (name.equals(o) || getPluralName().equals(o))
				return true;
			return false;
		}
		
		public boolean amThisType(int t) {
			if (type == t)
				return true;
			return false;
		}
		
		public static ActorType getType(String o) {
			if (AM.amThisType(o))
				return AM;
			if (SM.amThisType(o))
				return SM;
			if (BROKER.amThisType(o))
				return BROKER;
			if (ACTOR.amThisType(o))
				return ACTOR;
			return UNKNOWN;
		}
		
		public static ActorType getType(int t) {
			if (AM.amThisType(t))
				return AM;
			if (SM.amThisType(t))
				return SM;
			if (BROKER.amThisType(t))
				return BROKER;
			return UNKNOWN;
		}
	}
	
	public enum PropertyType {
		LOCAL("local"),
		CONFIGURATION("config"),
		REQUEST("request"),
		RESOURCE("resource"),
		ALL("all"),
		UNKNOWN("unknown");
		
		String name;
		PropertyType(String n) {
			name = n;
		}

		public boolean propertyThisType(String o) {
			return name.equals(o);
		}
		
		public String getName() {
			return name;
		}
		
		public static PropertyType getType(String o) {
			if (LOCAL.propertyThisType(o))
				return LOCAL;
			if (CONFIGURATION.propertyThisType(o))
				return CONFIGURATION;
			if (REQUEST.propertyThisType(o))
				return REQUEST;
			if (RESOURCE.propertyThisType(o))
				return RESOURCE;
			if (ALL.propertyThisType(o))
				return ALL;
			return UNKNOWN;
		}
	}
	
	public enum CurrentType {
		CONTAINER("container"),
		ACTOR("actor"),
		SLICE("slice"),
		RESERVATION("reservation"),
		UNKNOWN("unknown");
		
		String name;
		CurrentType(String n) {
			name = n;
		}
		
		public String getName() {
			return name;
		}
		
		public boolean currentThisType(String o) {
			return name.equals(o);
		}
		
		public static CurrentType getType(String o) {
			if (CONTAINER.currentThisType(o))
				return CONTAINER;
			if (ACTOR.currentThisType(o))
				return ACTOR;
			if (SLICE.currentThisType(o))
				return SLICE;
			if (RESERVATION.currentThisType(o))
				return RESERVATION;
			return UNKNOWN;
		}
	}
	
	public enum ReservationState {
		ACTIVE("active"),
		CLOSED("closed"),
		FAILED("failed"),
		ALL("all"),
		UNKNOWN("unknown");
		
		String name;
		ReservationState(String s) {
			name = s;
		}
		
		public String getName() {
			return name;
		}
		
		public boolean stateThisType(String o) {
			return name.equals(o);
		}
		
		public static ReservationState getType(String o) {
			if (ACTIVE.stateThisType(o))
				return ACTIVE;
			if (CLOSED.stateThisType(o))
				return CLOSED;
			if (FAILED.stateThisType(o))
				return FAILED;
			return UNKNOWN;
		}
	}
}
