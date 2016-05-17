package cloudsync.connector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import cloudsync.exceptions.FileIOException;

import cloudsync.exceptions.CloudsyncException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Handler;
import cloudsync.model.Item;
import cloudsync.model.ItemType;
import cloudsync.model.RemoteItem;
import cloudsync.model.LocalStreamData;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import org.apache.commons.io.FileUtils;

public class RemoteLocalFilesystemConnector implements RemoteConnector {

    private final static Logger LOGGER = Logger.getLogger(RemoteLocalFilesystemConnector.class.getName());

    /**
     * By using a format with only y/M/d, we will create an history folder per day of launch, 
     * this make more sense than a folder for each launch.
     */
    private static String HISTORY_DATE_FORMAT = "yyyy.MM.dd";
    
    final static int			MIN_SEARCH_BREAK			= 5000;
    final static int			MIN_SEARCH_RETRIES			= 12;    
    
    //private Map<String, File> cacheFiles;
    //private Map<String, File> cacheParents;

    private File remoteBackupFolder; 
    private File remoteBackupHistoryFolder;
    private String remoteTargetFolder;
    private String backupName;
    private Integer historyCount;
    private long lastValidate = 0;
    //private boolean showProgress;
    private int retries;
    private int waitretry;

    public RemoteLocalFilesystemConnector() {
    }

    @Override
    public void init(String backupName, CmdOptions options) throws CloudsyncException {
        RemoteLocalFilesystemOptions localFilesystemOptions = new RemoteLocalFilesystemOptions(options, backupName);
        Integer history = options.getHistory();

        //showProgress = options.showProgress();
        retries = options.getRetries();
        waitretry = options.getWaitRetry() * 1000;

        //cacheFiles = new HashMap<String, File>();
        //cacheParents = new HashMap<String, File>();

        this.remoteTargetFolder = localFilesystemOptions.getTargetFolder();
        this.backupName = backupName;
        this.remoteBackupFolder = new File(new File(remoteTargetFolder),this.backupName);
        this.remoteBackupFolder.mkdirs();
        this.historyCount = history;
        this.remoteBackupHistoryFolder = this.historyCount > 0 ? new File(new File(remoteTargetFolder),backupName+"_history_"+new SimpleDateFormat(HISTORY_DATE_FORMAT).format(new Date())) : null;
    }

    @Override
    public void upload(final Handler handler, final Item item) throws CloudsyncException, FileIOException {
        String title = handler.getLocalProcessedTitle(item);
        File remoteFile = new File(_getRemoteFile(item.getParent()), title);
        File remoteMetadataFile = new File(remoteFile.getParent(), remoteFile.getName() + ".metadata");
        int retryCount = 0;
        do {
            try {
                if(ItemType.FOLDER.equals(item.getType())) {
                    remoteFile.mkdirs();
                }
                else {
                    LocalStreamData data = handler.getLocalProcessedBinary(item);
                    java.nio.file.Files.copy(data.getStream(),remoteFile.toPath());
                }

                final String metadata = handler.getLocalProcessedMetadata(item);
                java.nio.file.Files.write(remoteMetadataFile.toPath(), metadata.getBytes("UTF-8"),StandardOpenOption.CREATE_NEW);
                
                if (!remoteFile.exists()) {
                    throw new CloudsyncException("Couldn't create item '" + item.getPath() + "'");
                }
                if(!remoteMetadataFile.exists()) {
                    throw new CloudsyncException("Couldn't create metadata for item '" + item.getPath() + "'");
                }
                
                //_addToCache(driveItem, null);
                item.setRemoteIdentifier(remoteFile.getName());
                return;
            } catch (final IOException e) {
                for (int i = 0; i < MIN_SEARCH_RETRIES; i++) {
                    if (remoteFile.exists() || remoteMetadataFile.exists()) {
                        LOGGER.log(Level.WARNING, "RemoteLocaFilesystem IOException: " + getExceptionMessage(e) + " - found partially remote item - try to update");
                        item.setRemoteIdentifier(remoteFile.getName());
                        update(handler, item, true);
                        return;
                    }
                    LOGGER.log(Level.WARNING, "RemoteLocaFilesystem IOException: " + getExceptionMessage(e) + " - item not uploaded - retry " + (i + 1) + "/" + MIN_SEARCH_RETRIES + " - wait "
                            + MIN_SEARCH_BREAK + " ms");
                    sleep(MIN_SEARCH_BREAK);
                }
                retryCount = validateException("remote upload", item, e, retryCount);
            }
        } while (true);
    }

    @Override
    public void update(final Handler handler, final Item item, final boolean with_filedata) throws CloudsyncException, FileIOException {
        final File remoteFile = _getRemoteFile(item);
        final File remoteMetadataFile = new File(remoteFile.getParent(), remoteFile.getName() + ".metadata");
        
        int retryCount = 0;
        do {
            try {
                if (item.isType(ItemType.FILE)) {
                    if (remoteBackupHistoryFolder != null) {
                        _moveToHistory(item);
                    }
                }

                LocalStreamData data = handler.getLocalProcessedBinary(item);
                java.nio.file.Files.copy(data.getStream(),remoteFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
                final String metadata = handler.getLocalProcessedMetadata(item);
                java.nio.file.Files.write(remoteMetadataFile.toPath(), metadata.getBytes("UTF-8"),StandardOpenOption.TRUNCATE_EXISTING);                
                
                if (!remoteFile.exists() && ! remoteMetadataFile.exists()) {
                    throw new CloudsyncException("Couldn't update item '" + item.getPath() + "'");
                }
                //_addToCache(driveItem, null);
                return;
            } catch (final IOException e) {
                retryCount = validateException("remote update", item, e, retryCount);
            }
        } while (true);
    }
    
    private void _moveToHistory(final Item item) throws IOException,CloudsyncException {
        if (remoteBackupHistoryFolder != null) {
            final File remoteFile = _getRemoteFile(item);
            final File remoteFileMetadata = new File(remoteFile.getParent(),remoteFile.getName()+".metadata");
            final File historyRemoteFile = _getRemoteHistoryFile(item);
            final File historyRemoteFileMetadata = new File(historyRemoteFile.getParent(),historyRemoteFile.getName()+".metadata");

            remoteBackupHistoryFolder.mkdirs();
            historyRemoteFile.getParentFile().mkdirs();
            
            java.nio.file.Files.copy(remoteFile.toPath(), historyRemoteFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(remoteFileMetadata.toPath(), historyRemoteFileMetadata.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            if (!historyRemoteFile.exists()) {
                throw new CloudsyncException("Couldn't make a history snapshot of item '" + item.getPath() + "'");
            }
            if (!historyRemoteFileMetadata.exists()) {
                throw new CloudsyncException("Couldn't make a history snapshot of metadata item '" + item.getPath() + "'");
            }
        }
    }

    @Override
    public void remove(final Handler handler, final Item item) throws CloudsyncException {
        int retryCount = 0;
        do {
            try {
                final File remoteFile = _getRemoteFile(item);
                final File remoteFileMetadata = new File(remoteFile.getParent(),remoteFile.getParent()+".metadata");
            
                if (remoteBackupHistoryFolder != null) {
                    _moveToHistory(item);
                }
                
                remoteFile.delete();
                remoteFileMetadata.delete();
                //_removeFromCache(item.getRemoteIdentifier());
                return;
            } 
            catch (final IOException e) {
                retryCount = validateException("remote remove", item, e, retryCount);
            }
        } while (true);
    }

    @Override
    public InputStream get(final Handler handler, final Item item) throws CloudsyncException {
        int retryCount = 0;
        do {
            try {
                final File remoteFileItem = _getRemoteFile(item);
                return new FileInputStream(remoteFileItem);
            } catch (final IOException e) {
                retryCount = validateException("remote get", item, e, retryCount);
            }
        } while (true);
    }

    @Override
    public List<RemoteItem> readFolder(final Handler handler, final Item parentItem) throws CloudsyncException {
        int retryCount = 0;
        do {
            try {
                final List<RemoteItem> child_items = new ArrayList<>();
                File remoteFolder = _getRemoteFile(parentItem);
                for (final File child : remoteFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if(pathname.getName().endsWith(".metadata")) {
                            return false;
                        }
                        return true;
                    }
                })) {
                    child_items.add(_prepareBackupItem(child, handler));
                }
                return child_items;
            } 
            catch (final Exception e) {
                retryCount = validateException("remote fetch", parentItem, new IOException("Cannot read remote folder", e), retryCount);
            }
        } while (true);
    }

    @Override
    public void cleanHistory(final Handler handler) throws CloudsyncException {
        File rootTarget = remoteBackupFolder.getParentFile();
        final SimpleDateFormat sdf = new SimpleDateFormat(HISTORY_DATE_FORMAT);
        final int backupHistoryNamePrefix = (backupName+"_history_").length();
                    
        try {
            final List<File> child_items = new ArrayList<File>();
            for (File f : rootTarget.listFiles()) {
                if(f.getName().startsWith(backupName+"_history_")) {
                    sdf.parse(f.getName().substring(backupHistoryNamePrefix)); // this will throw an Unexpected error
                    child_items.add(f);
                }
            }

            if (child_items.size() > historyCount) {
                    
                    Collections.sort(child_items, new Comparator<File>() {
                        @Override
                        public int compare(final File o1, final File o2) {
                            try {
                                Date d1 = sdf.parse(o1.getName().substring(backupHistoryNamePrefix));
                                Date d2 = sdf.parse(o2.getName().substring(backupHistoryNamePrefix));
                                
                                final long v1 = d1.getTime();
                                final long v2 = d2.getTime();

                                if (v1 < v2) return 1;
                                if (v1 > v2) return -1;
                            }
                            catch(Exception e) {
                                LOGGER.severe("Error parsing datetime for history folder");
                            }
                            return 0;
                        }
                    });

                    for (File file : child_items.subList(historyCount, child_items.size())) {
                        LOGGER.log(Level.FINE, "cleanup history folder '" + file.getName() + "'");
                        FileUtils.deleteDirectory(file);
                    }
            }
        }
        catch (final Exception e) {
            throw new CloudsyncException("Unexpected error during history cleanup", e);
        }
    }
    
    private File _getRemoteFile(Item item) {
        if(item.getParent() == null) {
            return new File(remoteBackupFolder,item.getRemoteIdentifier());
        }
        return new File(_getRemoteFile(item.getParent()),item.getRemoteIdentifier());
    }
    
    private File _getRemoteHistoryFile(final Item item) throws CloudsyncException, IOException {
        if (remoteBackupHistoryFolder == null) {
            return null;
        }

        if(item.getParent() == null) {
            return new File(remoteBackupHistoryFolder,item.getRemoteIdentifier());
        }
        return new File(_getRemoteHistoryFile(item.getParent()),item.getRemoteIdentifier());
    }
    

    private RemoteItem _prepareBackupItem(final File remoteFile, final Handler handler) throws CloudsyncException {
        try {
            String metadata = null;
            File remoteMetadataFile = new File(remoteFile.getParent(),remoteFile.getName() + ".metadata");
            String encryptedMetadata = new String(Files.readAllBytes(remoteMetadataFile.toPath()),"UTF-8");
            metadata = handler.getProcessedText(encryptedMetadata);
            String title = handler.getProcessedText(remoteFile.getName());
            return handler.initRemoteItem(remoteFile.getName(), remoteFile.isDirectory(), title, metadata, remoteFile.length(),FileTime.fromMillis(remoteFile.lastModified()));
        } catch (Exception e) {
            throw new CloudsyncException("Can't decrypt infos about '" + remoteFile.getName());
        }
    }

    
    



    /*private void _removeFromCache(final String id) {
        cacheFiles.remove(id);
    }

    private void _addToCache(final File driveItem, final File parentDriveItem) {
        if (driveItem.isDirectory()) {
            cacheFiles.put(driveItem.getPath(), driveItem);
        }
        if (parentDriveItem != null) {
            cacheParents.put(parentDriveItem.getPath() + ':' + driveItem.getPath(), driveItem);
        }
    }*/

    private void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
        }
    }

    private int validateException(String name, Item item, IOException e, int count) throws CloudsyncException {
        if (count < retries) {
            long currentValidate = System.currentTimeMillis();
            long current_retry_break = (currentValidate - lastValidate);
            if (lastValidate > 0 && current_retry_break < waitretry) {
                sleep(waitretry - current_retry_break);
            }

            lastValidate = currentValidate;

            count++;

            LOGGER.log(Level.WARNING, "RemoteLocaFilesystem IOException: " + getExceptionMessage(e) + " - " + name + " - retry " + count + "/" + retries);

            return count;
        }

        if (item != null) {
            throw new CloudsyncException("Unexpected error during " + name + " of " + item.getTypeName() + " '" + item.getPath() + "'", e);
        } else {
            throw new CloudsyncException("Unexpected error during " + name, e);
        }
    }

    private String getExceptionMessage(IOException e) {

        String msg = e.getMessage();
        if (msg.contains("\n")) {
            msg = msg.split("\n")[0];
        }
        return "'" + msg + "'";
    }




}
