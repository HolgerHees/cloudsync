cloudsync
=========

Sync a local filesystem on linux, windows and osx incremental and encrypted with google drive simliar to rsync. You can also restore the encrypted data back to a local filesystem. It works as a complete backup solution for your private data.

Other compareable backup solution like [duplicity](http://duplicity.nongnu.org) are uploading one big encrypted 'base' archive with additional delta archive files. After some month you must upload a new fresh 'base' archive to avoid hundred of delta files. This is problematic for private async dsl connections. To solve these issues each file is encrypted and uploaded separately.

To get a first impression you should take a look at [this screenshot](https://github.com/HolgerHees/cloudsync/wiki/Home).

The encryption is based on OpenPGP with AES 256 and a passphrase. It is possible to decrypt uploaded files with a normal OpenPGP compatible tool like 'gpg' or 'gpg2'.

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

**java >= 7 and Java Cryptography Extension (JCE) is required**

To use it, copy ```'config/cloudsync.config.default'``` to ```'config/cloudsync.config'``` and set your PASSPHRASE, GOOGLE_DRIVE_CLIENT_ID and GOOGLE_DRIVE_CLIENT_SECRET. To get the last two parameter follow https://github.com/HolgerHees/cloudsync/wiki/Google-Client-Credentials. Finally to enable the Google Drive API follow https://github.com/HolgerHees/cloudsync/wiki/Google-Drive-API.

to create a backup of '/data', call:

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
    --dry-run                              Perform a trial run of --backup or --restore with no changes made.
    --progress                             Show progress during transfer and encryption.
    --retries <number>                     Number of network operation retries before an error is thrown (default: 6).
    --waitretry <seconds>                  Number of seconds between 2 retries (default: 10).
    --ask-to-continue                      Show a command prompt (Y/n) instead of throwing an error on network
                                           connection problems.
    --logfile <path>                       Log message to <path>
    --cachefile <path>                     Cache data to <path>
 -h,--help
 ```
