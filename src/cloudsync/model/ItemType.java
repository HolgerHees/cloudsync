package cloudsync.model;

public enum ItemType {

	UNKNOWN(0, "unknown", "unknown"), FOLDER(1, "folder", "folder"), FILE(2, "file", "files"), LINK(3, "link", "links"), DUPLICATE(99, "duplicate", "duplicates");

	private Integer value;
	private String name;
	private String namePlural;

	private ItemType(final Integer value, final String name, final String namePlural) {
		this.value = value;
		this.name = name;
		this.namePlural = namePlural;
	}

	@Override
	public String toString() {

		return value.toString();
	}

	public String getName() {

		return name;
	}

	public String getName(Integer count) {

		return count == 1 ? name : namePlural;
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
