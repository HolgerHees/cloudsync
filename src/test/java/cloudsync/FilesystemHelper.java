package cloudsync;

public class FilesystemHelper {
    private static String OS = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    public static String fixPathSeparators(String path) {
        if (isWindows()) {
            return path.replace("\\", "\\\\");
        }
        return path;
    }
}
