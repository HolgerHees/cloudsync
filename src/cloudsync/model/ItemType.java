package cloudsync.model;

public enum ItemType {

	UNKNOWN(0, "unknown"), FOLDER(1, "folder"), FILE(2, "file"), LINK(3, "link");

	private Integer value;
	private String name;

	private ItemType(final Integer value, final String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public String toString() {

		return value.toString();
	}

	public String getName() {

		return name;
	}

	public static ItemType fromString(final String value) {

		final Integer intValue = Integer.parseInt(value);

		for (final ItemType type : ItemType.values()) {

			if (!type.value.equals(intValue)) {
				continue;
			}

			return type;
		}

		return null;
	}
}
