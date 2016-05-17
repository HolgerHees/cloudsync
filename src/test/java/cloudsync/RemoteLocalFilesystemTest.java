package cloudsync;


import cloudsync.exceptions.CloudsyncException;
import cloudsync.exceptions.FileIOException;
import cloudsync.exceptions.InfoException;
import cloudsync.exceptions.UsageException;
import cloudsync.helper.CmdOptions;
import cloudsync.helper.Crypt;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;


public class RemoteLocalFilesystemTest {
    
    public RemoteLocalFilesystemTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() throws IOException {
    }
    
    @After
    public void tearDown() throws IOException {
    }

    
    
    /**
     * Generate a random hierarchy, backup then restore it. 
     * Check for equality between the origin hierarchy and restored one.
     */
    @Test
    public void test1() throws IOException, ParseException {
        File rootFolder = createRandomHierarchie();
        
        File targetLocalRemoteFolder = Files.createTempDirectory("targetRemoteFolder").toFile();
        File restoreFolder = new File(rootFolder.getParent(),rootFolder.getName()+"_restore");
        File configFile = Files.createTempFile("test1Config", ".config").toFile();
        
        String config = "REMOTE_CONNECTOR=LocalFilesystem";
        config += "\n"+ "PASSPHRASE=1234567";
        config += "\n"+ "TARGET_DIR=" + targetLocalRemoteFolder.getAbsolutePath();
        config += "\n"+ "CACHEFILE="+targetLocalRemoteFolder.getAbsolutePath() + File.separator + ".cloudsync_{name}.cache";
        config += "\n"+ "LOGFILE="+targetLocalRemoteFolder.getAbsolutePath() + File.separator + ".cloudsync_{name}.log";
        Files.write(configFile.toPath(), config.getBytes(), StandardOpenOption.CREATE);
                
        System.out.println("RootFolder: " + rootFolder.getAbsolutePath());
        System.out.println("TargetFolder: " + targetLocalRemoteFolder.getAbsolutePath());

        
        String[] args = new String[]{
            "--nocache",
            "--backup",
            rootFolder.getAbsolutePath(),
            "--name",
            "Test1Backup",
            "--config",
            configFile.getAbsolutePath()
        };
        Cloudsync.main(args);
        
        
        restoreFolder.mkdir();
        args = new String[]{
            "--nocache",
            "--restore",
            restoreFolder.getAbsolutePath(),
            "--name",
            "Test1Backup",
            "--config",
            configFile.getAbsolutePath()
        };
        Cloudsync.main(args);
        
        
        assertTrue(hierarchieEquals(rootFolder, restoreFolder));
        FileUtils.deleteDirectory(rootFolder);
    }
    
    
    /**
     * Create a specific hierarchy
     * Modify files
     * Check if file is in history.
     */
    @Test
    public void test2() throws IOException, ParseException, CloudsyncException, FileIOException, UsageException, InfoException {
        File rootFolder = Files.createTempDirectory("srcTest2").toFile();
        File targetLocalRemoteFolder = Files.createTempDirectory("targetRemoteFolder").toFile();
        File restoreFolder = new File(rootFolder.getParent(),rootFolder.getName()+"_restore");
        File configFile = Files.createTempFile("test2Config", ".config").toFile();
        
        System.out.println("Test2");
        System.out.println("RootFolder: " + rootFolder.getAbsolutePath());
        System.out.println("TargetFolder: " + targetLocalRemoteFolder.getAbsolutePath());
        
        String config = "REMOTE_CONNECTOR=LocalFilesystem";
        config += "\n"+ "PASSPHRASE=1234567";
        config += "\n"+ "TARGET_DIR=" + targetLocalRemoteFolder.getAbsolutePath();
        config += "\n"+ "CACHEFILE="+targetLocalRemoteFolder.getAbsolutePath() + File.separator + ".cloudsync_{name}.cache";
        config += "\n"+ "LOGFILE="+targetLocalRemoteFolder.getAbsolutePath() + File.separator + ".cloudsync_{name}.log";
        config += "\n"+ "HISTORY=3";
        Files.write(configFile.toPath(), config.getBytes(), StandardOpenOption.CREATE);
           
        /**
         * rootFolder
         *  /file1
         *  /subfolder1
         *      /file2
         *      /subfolder2
         *          /file3
         */
        File file1 = new File(rootFolder,"file1");
        File subfolder1 = new File(rootFolder,"subfolder1");
        File file2 = new File(subfolder1,"file2");
        File subfolder2 = new File(subfolder1,"subfolder2");
        File file3 = new File(subfolder2,"file3");
        subfolder1.mkdirs();
        subfolder2.mkdirs();
        
        Files.write(file1.toPath(), "file 1 content".getBytes("UTF-8"), StandardOpenOption.CREATE_NEW);
        Files.write(file2.toPath(), "file 2 content".getBytes("UTF-8"), StandardOpenOption.CREATE_NEW);
        Files.write(file3.toPath(), "file 3 content".getBytes("UTF-8"), StandardOpenOption.CREATE_NEW);
        
        String[] args = new String[]{
            "--nocache",
            "--backup",
            rootFolder.getAbsolutePath(),
            "--name",
            "Test2Backup",
            "--config",
            configFile.getAbsolutePath()
        };
        Cloudsync.main(args);

        // Update files content
        Files.write(file1.toPath(), "new file 1 content updated".getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(file2.toPath(), "new file 2 content updated".getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(file3.toPath(), "new file 3 content updated".getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING);
        Cloudsync.main(args);
        
        // Check if history exist for updated files
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        File historyFolder = new File(targetLocalRemoteFolder,"Test2Backup"+"_history_"+sdf.format(new Date()));
        
        CmdOptions cmdOptions = new CmdOptions(args);
        cmdOptions.parse();
        Crypt crypt = new Crypt(cmdOptions);
        assertEquals(historyFolder.listFiles().length,3);
        for(File f1 : historyFolder.listFiles()) {
            if(f1.getName().endsWith(".metadata")) {
                //TODO
            }
            else if(f1.isFile()) {
                assertEquals(crypt.decryptText(f1.getName()), "file1");
                _assertEncryptedFileContentEquals(f1,crypt,"file 1 content");
            }
            else {
                assertEquals(crypt.decryptText(f1.getName()), "subfolder1");
                assertEquals(f1.listFiles().length,3);
                for(File f2 : f1.listFiles()) {
                    if(f2.getName().endsWith(".metadata")) {
                        //TODO
                    }
                    else if(f2.isFile()) {
                        assertEquals(crypt.decryptText(f2.getName()), "file2");
                        _assertEncryptedFileContentEquals(f2,crypt,"file 2 content");
                    }
                    else {
                        assertEquals(f2.listFiles().length,2);
                        assertEquals(crypt.decryptText(f2.getName()), "subfolder2");
                        for(File f3 : f2.listFiles()) {
                            if(f3.getName().endsWith(".metadata")) {
                                //TODO
                            }
                            else {
                                assertEquals(crypt.decryptText(f3.getName()), "file3");
                                _assertEncryptedFileContentEquals(f3,crypt,"file 3 content");
                            }
                        }
                    }
                }
            }
        }     
        
        restoreFolder.mkdir();
        args = new String[]{
            "--nocache",
            "--restore",
            restoreFolder.getAbsolutePath(),
            "--name",
            "Test2Backup",
            "--config",
            configFile.getAbsolutePath()
        };
        Cloudsync.main(args);
        
        
        assertTrue(hierarchieEquals(rootFolder, restoreFolder));
        FileUtils.deleteDirectory(rootFolder);
    }
   
    private void _assertEncryptedFileContentEquals(File file, Crypt crypt, String content) throws IOException, CloudsyncException {
        byte[] encryptedFileContent = Files.readAllBytes(file.toPath());
        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedFileContent);
        byte[] fileContent = IOUtils.toByteArray(crypt.decryptData(bais));
        String s = new String(fileContent, "UTF-8");
        assertEquals(s,content);
    }
    
    
    public static File createRandomHierarchie() throws IOException {
        Random random = new Random();

        int prefixLength = random.nextInt(10)+1;
        String prefix = "src"+org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(prefixLength);

        File root = Files.createTempDirectory(prefix).toFile();
        root.mkdirs();
        createRandomHierarchie(root,(random.nextDouble()+1.)*3.);
        return root;
    }

    public static void createRandomHierarchie(File parent, double nbFolderProba) throws IOException {
        Random random = new Random();

        double r = random.nextDouble();
        while(r < nbFolderProba) {
            nbFolderProba = nbFolderProba / (random.nextDouble() + 1.);
            File folder = createRandomFolder(parent,random.nextInt(20));
            createRandomHierarchie(folder,nbFolderProba);
            r = random.nextDouble();
        }
    }
    
    
    
    public static void createRandomFile(File inFolder) throws IOException {
        Random random = new Random();
        
        int prefixLength = random.nextInt(10)+3;
        String prefix = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(prefixLength);
        String suffix = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(3);
        File f = File.createTempFile(prefix, suffix, inFolder);
        
        
        int n = random.nextInt(50);
        while(n != 0) {
            int l = random.nextInt(1000);
            byte[] data = new byte[l];
            random.nextBytes(data);
            Files.write(f.toPath(), data, StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            n = n - 1;
        }
    }

    
    public static File createRandomFolder(File inFolder, int nbFile) throws IOException {
        Random random = new Random();
        int prefixLength = random.nextInt(10)+1;
        String prefix = org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric(prefixLength);
        File result = Files.createTempDirectory(inFolder.toPath(), prefix).toFile();
        result.mkdirs();
        for(int i = 0;i<nbFile;i++) {
            createRandomFile(result);
        }
        return result;
    }
    
    
    public static boolean hierarchieEquals(File root1,File root2) throws IOException {
        File[] files1 = root1.listFiles();
        File[] files2 = root2.listFiles();
        if(files1.length != files2.length) {
            return false;
        }
        
        Map<String,File> mapFiles2 = new HashMap<>();
        for(File f : files2) {
            mapFiles2.put(f.getName(), f);
        }
        
        for(File f1 : files1) {
            if(!mapFiles2.containsKey(f1.getName())) {
                return false;
            }
            
            File f2 = mapFiles2.get(f1.getName());
            if(f1.isDirectory() && f2.isDirectory()) {
                if(!hierarchieEquals(f1,f2)) {
                    return false;
                }
            }
            else if(f1.isFile() && f2.isFile()) {
                if(!FileUtils.contentEquals(f1, f2)) {
                    return false;
                }
            }
            else {
                return false;
            }
            
            mapFiles2.remove(f1.getName());
        }
        
        if(!mapFiles2.isEmpty()) {
            return false;
        }
        
        return true;
    }
    
}
