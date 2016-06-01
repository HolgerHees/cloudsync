package cloudsync.connector;

import org.apache.commons.lang3.StringUtils;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;

public class RemoteLocalFilesystemOptions {

    private String targetFolder = null;

    public RemoteLocalFilesystemOptions(CmdOptions options, String name) throws CloudsyncException {
        targetFolder = options.getProperty("TARGET_DIR");
        if (StringUtils.isEmpty(targetFolder)) {
            throw new CloudsyncException(prepareMessage("TARGET_DIR"));
        }
    }

    private String prepareMessage(String name) {
        return "'" + name + "' is not configured";
    }

    public String getTargetFolder() {
        return targetFolder;
    }

}
