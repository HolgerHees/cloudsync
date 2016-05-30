# cloudsync

Sync a local filesystem on linux, windows and osx incremental and encrypted with google drive simliar to rsync. You can also restore the encrypted data back to a local filesystem. It works as a complete backup solution for your private data.

Other compareable backup solutions like [duplicity](http://duplicity.nongnu.org) upload one big encrypted 'base' archive with additional delta archive files. After a few months you must upload a new fresh 'base' archive to avoid hundreds of delta files. This approach, while widely used in various backup solutions, is problematic for private async DSL connections. To solve these issues each file is encrypted and uploaded separately.

To get a first impression you should take a look at [this screenshot](https://github.com/HolgerHees/cloudsync/wiki/Home).

The encryption is based on OpenPGP with AES 256 (optional) and a passphrase. It is possible to decrypt uploaded files with a normal OpenPGP compatible tool like 'gpg' or 'gpg2'.

Encrypted data includes:
- filetype [folder,file,symlink]
- filecontent, filename and original filesize
- createtime, modifytime and accesstime
- owner, group, posix permissions, acl entries and fat32 attributes
- md5 checksum

Filechanges are detected by comparing the file metadata. It uses a local cachefile to speedup the incremental update. The local cachefile is completly restoreable by analysing the serverside archived metadata.

Supported Cloud Services are:
- Google Drive (stable)
- Dropbox (alpha preview)

To provide additional cloud targets like Amazon Cloud Drive or Microsoft OneDrive just implement 6 functions from the interface [Connector.java](https://github.com/HolgerHees/cloudsync/tree/master/src/main/java/cloudsync/connector/RemoteConnector.java).

## Requirements

1. Java >= 7
2. [Java Cryptography Extension (JCE)](http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html) (optional to support AES256)
3. [Maven](http://maven.apache.org/download.cgi)

## Install

```bash
git clone https://github.com/HolgerHees/cloudsync.git
cd cloudsync
mvn install
```

## Config

To use, copy ```'config/cloudsync.config.default'``` to ```'config/cloudsync.config'``` and set the following;

- `PASSPHRASE`: your master password used for encrypt/decrypt

There are two authentication options, either using an Installed Application or Service Account.

Both options require that you [enable the Google Drive API](https://github.com/HolgerHees/cloudsync/wiki/Google-Drive-API). 

### Installed Application

Follow [these instructions](https://github.com/HolgerHees/cloudsync/wiki/Google-Client-Credentials) to set the following;

- `GOOGLE_DRIVE_CLIENT_ID`
- `GOOGLE_DRIVE_CLIENT_SECRET`

### Service Account

This process requires more configuration however will ensure you can run unattended.

1. [Create Service Account](https://developers.google.com/identity/protocols/OAuth2ServiceAccount). You must ensure that you [delegate domain-wide authority to your service account](https://developers.google.com/drive/web/delegation#delegate_domain-wide_authority_to_your_service_account).
2. Download P12 keyfile.
3. Set the following in the configuration file.

- `GOOGLE_DRIVE_SERVICE_ACCOUNT_EMAIL` - ie.. `<some-id>@developer.gserviceaccount.com`.
- `GOOGLE_DRIVE_SERVICE_ACCOUNT_USER` - The Google Drive user, ie.. `you@yourdomain.com`.
- `GOOGLE_DRIVE_SERVICE_ACCOUNT_PRIVATE_KEY_P12_PATH` - The full or relative path to your P12 file.

## Usage

To create a backup of '/data', call:

```./cloudsync --backup /data --name dataBackup```

to restore a backup into '/restore', call:

```./cloudsync --restore /restore --name dataBackup```

for a complete list of options, see below:

```
usage: cloudsync <options>
 -b,--backup <path>                        Create or refresh backup of <path>
 -r,--restore <path>                       Restore a backup into <path>
 -c,--clean <path>                         Repair 'cloudsync*.cache' file and put leftover file into <path>
 -l,--list                                 List the contents of an backup
 -n,--name <name>                          Backup name of --backup, --restore, --clean or --list
    --config <path>                        Config file path. Default is './config/cloudsync.config'
    --followlinks <extern|all|none>        How to handle symbolic links
                                           <extern> - follow symbolic links if the target is outside from the current
                                           directory hierarchy - (default)
                                           <all> - follow all symbolic links
                                           <none> - don't follow any symbolic links
    --existing <stop|update|skip|rename>   Behavior on files that exists localy during --restore
                                           <stop> - stop immediately - (default)
                                           <update> - replace file
                                           <skip> - skip file
                                           <rename> - extend the name with an autoincrement number
    --history <count>                      Before remove or update a file or folder move it to a history folder.
                                           Use a maximum of <count> history folders
    --include <pattern>                    Include content of --backup, --restore and --list if the path matches the
                                           regex based ^<pattern>$. Multiple patterns can be separated with an '|'
                                           character.
    --exclude <pattern>                    Exclude content of --backup, --restore and --list if the path matches the
                                           regex based ^<pattern>$. Multiple patterns can be separated with an '|'
                                           character.
    --permissions <set|ignore|try>         Behavior how to handle acl permissions during --restore
                                           <set> - set all permissions and ownerships - (default)
                                           <ignore> - ignores all permissions and ownerships
                                           <try> - ignores invalid and not assignable permissions and ownerships
    --nocache                              Don't use 'cloudsync*.cache' file for --backup or --list (much slower)
    --forcestart                           Ignore a existing pid file. Should only be used after a previous crashed job.
    --noencryption                         Don't encrypt uploaded data
    --dry-run                              Perform a trial run of --backup or --restore with no changes made.
    --progress                             Show progress during transfer and encryption.
    --retries <number>                     Number of network operation retries before an error is thrown (default: 6).
    --waitretry <seconds>                  Number of seconds between 2 retries (default: 10).
    --network-error <exception|ask>        How to continue on network problems
                                           <exception> - Throw an exception - (default)
                                           <ask> - Show a command prompt (Y/n) to continue
    --file-error <exception|message>       How to continue on blocked files or permission problems
                                           <exception> - Throw an exception - (default)
                                           <message> - Show a error log message
    --logfile <path>                       Log message to <path>
    --cachefile <path>                     Cache data to <path>
 -v,--version                              Show version number
 -h,--help                                 Show this help
 ```
