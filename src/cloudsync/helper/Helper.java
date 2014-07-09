package cloudsync.helper;

import org.apache.commons.lang3.StringUtils;

public class Helper {

	public static String trim(String text, final String character) {

		text = StringUtils.removeStart(text, character);
		text = StringUtils.removeEnd(text, character);
		return text;
	}
}
