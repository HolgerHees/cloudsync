package cloudsync.model;

public enum ExistingBehaviorType {

	STOP("stop"), UPDATE("update"),SKIP("skip"), RENAME("rename");

	private String name;

	private ExistingBehaviorType(final String name) {
		this.name = name;
	}

	public String getName() {

		return name;
	}

	public static ExistingBehaviorType fromName(final String name) {

		for (final ExistingBehaviorType type : ExistingBehaviorType.values()) {

			if (!type.name.equals(name)) {
				continue;
			}

			return type;
		}

		return null;
	}
}
