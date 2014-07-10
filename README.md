cloudsync
=========

Sync a linux filesystem incremental and encrypted with google drive simliar to rsync.

It encrypt and decrypt the file, the filenames and all archived metadata with strong AES 256 encryption and a passphrase.

Archived metadata are:
- filesize
- modifytime
- createtime 
- gid
- uid
- permissions

The filecompare is done by comparing the archived metadata. It uses a local cachefile to speedup the incremental update. The local cachefile is completly restoreable by analysing the serverside archived metadata.

By default, the entire cache is every 7 days completely rebuilt based on serverside archived metadata. This is usually not necessary but gives a secure feeling to be sure that all is fine :-)

**java >= 7 and Java Cryptography Extension (JCE)**

```
usage: cloudsync <options>
 -b,--backup <path>                    Create or refresh backup of <path>
 -r,--restore <path>                   Restore a backup into <path>
 -c,--clean <path>                     Repair 'cloudsync*.cache' file and put leftover file into <path>
 -l,--list                             List the contents of an backup
 -n,--name <name>                      Backup name of --backup, --restore, --clean or --list
    --config <path>                    Config file path. Default is './config/cloudsync.config'                                                                                   
    --limit <pattern>                  Limit contents paths of --restore or --list to regex based ^<pattern>$                                                                     
    --followlinks <extern|all|none>    How to handle symbolic links                                                                                                               
                                       <extern> - convert external links where the target is not part of the current                                                              
                                       backup structure - (default)                                                                                                               
                                       <all> - convert all symbolic links                                                                                                         
                                       <none> - convert no symbolic links to real files or folders                                                                                
    --duplicate <stop|update|rename>   Behavior on existing files                                                                                                                 
                                       <stop> - stop immediately - (default)                                                                                                      
                                       <update> - replace file                                                                                                                    
                                       <rename> - extend the name with an autoincrement number
    --history <count>                  Before remove or update a file or folder move it to a history folder.
                                       Use a maximum of <count> history folders
    --nocache                          Don't use 'cloudsync*.cache' file for --backup or --list (much slower)
 -h,--help                             Show this help
```
