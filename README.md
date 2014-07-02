cloudsync
=========

sync a linux filesystem incrementel and encrypted with google drive

Call: ./cloudsync.php [OPTION]

Required arguments for long options are also required for short options.
    -b, --backup=<path>                            Create or refresh backup of <path>
    -r, --restore=<path>                           Restore backup into <path>
    -c, --clean=<path>                             Repair 'state*.backup' file and put leftover file into <path>

    -n, --name=<name>                              Backup name
        --nocache                                  Skip 'state*.backup' file (much slower)
        --duplicate=<stop>|<update>|<rename>       Behavior on existing files:
                                                        stop - stop immediately - (default)
                                                        update - replace file
                                                        rename - extend the name with an autoincrement number

    -h, --help                                     Show this help
