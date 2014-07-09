package cloudsync.model;

public enum DuplicateType {

	STOP("stop"), UPDATE("update"), RENAME("rename");

	private String name;

	private DuplicateType(final String name) {
		this.name = name;
	}

	public String getName() {

		return name;
	}

	public static DuplicateType fromName(final String name) {

		for (final DuplicateType type : DuplicateType.values()) {

			if (!type.name.equals(name)) {
				continue;
			}

			return type;
		}

		return null;
	}
}
