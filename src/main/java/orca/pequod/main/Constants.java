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
			for (ActorType at: ActorType.values())
				if (at.amThisType(o)) 
					return at;
			return UNKNOWN;
		}
		
		public static ActorType getType(int t) {
			for (ActorType at: ActorType.values())
				if (at.amThisType(t)) 
					return at;
			return UNKNOWN;
		}
		
		public String toString() {
			return name;
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
			for (PropertyType t: PropertyType.values()) {
				if (t.propertyThisType(o))
					return t;
			}
			return UNKNOWN;
		}
		
		public String toString() {
			return name;
		}
	}
	
	public enum CurrentType {
		CONTAINER("containers"),
		ACTOR("actors"),
		SLICE("slices"),
		RESERVATION("reservations"),
		UNKNOWN("unknown");
		
		String name;
		CurrentType(String n) {
			name = n;
		}
		
		public String getName() {
			return name;
		}
		
		// allow plural spelling
		public boolean currentThisType(String o) {
			return name.equals(o);
		}
		
		public static CurrentType getType(String o) {
			for (CurrentType t: CurrentType.values()) {
				if (t.currentThisType(o))
					return t;
			}
			return UNKNOWN;
		}
		
		public String toString() {
			return name;
		}
	}
	
	public enum ReservationState {
		NASCENT("nascent", 1),
		TICKETED("ticketed", 2),
		ACTIVE("active", 3),
		ACTIVETICKETED("activeticketed", 4),
		CLOSED("closed", 5),
		CLOSEWAIT("closewait", 6),
		FAILED("failed", 7),
		ALL("all", 0),
		UNKNOWN("unknown", -1);
		
		String name;
		int index;
		ReservationState(String s, int i) {
			name = s;
			index = i;
		}
		
		public String getName() {
			return name;
		}
		
		public int getIndex() {
			return index;
		}
		
		public boolean stateThisType(String o) {
			return name.equals(o);
		}
		
		public static ReservationState getState(int index) {
			for (ReservationState s: ReservationState.values()) {
				if (index == s.getIndex())
					return s;
			}
			return UNKNOWN;
		}
		
		public static ReservationState getType(String o) {
			for (ReservationState s: ReservationState.values()) {
				if (s.stateThisType(o))
					return s;
			}
			return UNKNOWN;
		}
		
		public String toString() {
			return name;
		}
	}
}
