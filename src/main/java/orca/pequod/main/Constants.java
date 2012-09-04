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
}
