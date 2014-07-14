package cloudsync.helper;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import cloudsync.model.Item;

public class Helper {

	public static String trim(String text, final String character) {

		text = StringUtils.removeStart(text, character);
		text = StringUtils.removeEnd(text, character);
		return text;
	}
	
	public static String getPathProperty( Properties prop, String key ){
		
		String path = prop.getProperty(key);
		if (path.startsWith("." + Item.SEPARATOR)) {
			path = System.getProperty("user.dir") + path.substring(1);
		}
		return path;
	}
}
