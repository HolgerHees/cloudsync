package cloudsync.model;

public enum SyncType {

	BACKUP("backup"), RESTORE("restore"), LIST("list"), CLEAN("clean");

	private String name;

	private SyncType(final String name) {
		this.name = name;
	}

	public String getName() {

		return name;
	}

	public static SyncType fromName(final String name) {

		for (final SyncType type : SyncType.values()) {

			if (!type.name.equals(name)) {
				continue;
			}

			return type;
		}

		return null;
	}
}
