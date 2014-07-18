cloudsync
=========

Sync a linux filesystem incremental and encrypted with google drive simliar to rsync.

It encrypt and decrypt the file, the filenames and all archived metadata with OpenPGP based AES 256 encryption and a passphrase. It is possible to decrypt archives with a normal OpenPGP compatible tool like 'gpg' or 'gpg2'.

Archived metadata are:
- filesize
- createtime 
- modifytime
- accesstime
- gid
- uid
- permissions

The filecompare is done by comparing the archived metadata. It uses a local cachefile to speedup the incremental update. The local cachefile is completly restoreable by analysing the serverside archived metadata.

**java >= 7 and Java Cryptography Extension (JCE)**

```
usage: cloudsync <options>
 -b,--backup <path>                    Create or refresh backup of <path>
 -r,--restore <path>                   Restore a backup into <path>
 -c,--clean <path>                     Repair 'cloudsync*.cache' file and put leftover file into <path>
 -l,--list                             List the contents of an backup
 -n,--name <name>                      Backup name of --backup, --restore, --clean or --list
    --config <path>                    Config file path. Default is './config/cloudsync.config'
    --followlinks <extern|all|none>    How to handle symbolic links
                                       <extern> - follow symbolic links if the target is outside from the current
                                       directory hierarchy - (default)
                                       <all> - follow all symbolic links
                                       <none> - don't follow any symbolic links
    --duplicate <stop|update|rename>   Behavior on existing files
                                       <stop> - stop immediately - (default for --backup and --restore)
                                       <update> - replace file
                                       <rename> - extend the name with an autoincrement number (default for --clean)
    --history <count>                  Before remove or update a file or folder move it to a history folder.
                                       Use a maximum of <count> history folders
    --include <pattern>                Include content of --backup, --restore and --list if the path matches the regex
                                       based ^<pattern>$. Multiple patterns can be separated with an '|' character.
    --exclude <pattern>                Exclude content of --backup, --restore and --list if the path matches the regex
                                       based ^<pattern>$. Multiple patterns can be separated with an '|' character.
    --nopermissions                    Don't restore permission, group and owner attributes
    --nocache                          Don't use 'cloudsync*.cache' file for --backup or --list (much slower)
    --forcestart                       Ignore a existing pid file. Should only be used after a previous crashed job.
    --logfile <path>                   Log message to <path>
    --test                             Start a 'test' run of --backup or --restore.
 -h,--help                             Show this help
```
