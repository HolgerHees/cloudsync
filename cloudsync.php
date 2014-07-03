#!/usr/bin/php
<?php
define ("CLASS_ROOT", __dir__.'/classes/');
function __autoload ($className)
{
    require_once CLASS_ROOT.$className.'.class.php';
}

$shortopts  = "hb:r:c:n:";
$longopts  = array( "help", "backup:", "restore:", "clean:", "name:", "nocache", "duplicate:","config:", "keeplinks:" );
$options = getopt($shortopts, $longopts);

$type = false;
$path = false;
if( isset($options['b']) || isset($options['backup']) ){
	$type = "backup";
	if( isset($options['b']) ) $path = $options['b'];
	else if( isset($options['backup']) ) $path = $options['backup'];
}
else if( isset($options['r']) || isset($options['restore']) ){
	$type = "restore";
	if( isset($options['r']) ) $path = $options['r'];
	else if( isset($options['restore']) ) $path = $options['restore'];
}
else if( isset($options['c']) || isset($options['clean']) ){
	$type = "clean";
	if( isset($options['c']) ) $path = $options['clean'];
	else if( isset($options['clean']) ) $path = $options['clean'];
}

$name = false;
if( isset($options['n']) ) $name = $options['n'];
else if( isset($options['name']) ) $name = $options['name'];

$duplicate = Structure::DUPLICATE_STOP;
if( isset($options['duplicate']) ) $duplicate=$options['duplicate'];
if( $duplicate != Structure::DUPLICATE_UPDATE && $duplicate != Structure::DUPLICATE_RENAME ) $duplicate = Structure::DUPLICATE_STOP;

$keeplinks = Structure::LINK_INTERNAL;
if( isset($options['keeplinks']) ) $keeplinks=$options['keeplinks'];
if( $keeplinks != Structure::LINK_ALL && $keeplinks != Structure::LINK_NONE ) $keeplinks = Structure::LINK_INTERNAL;

$nocache = false;
if( isset($options['nocache']) ) $nocache = true;

$nopermissions = false;
if( isset($options['nocache']) ) $nopermissions = true;

$config = false;
if( isset($options['config']) ) $config = $options['config'];
else if( is_file("/etc/cloudsync") ) $config = "/etc/cloudsync";
else if( is_file("~/.cloudsync.config") ) $config = "~/.cloudsync.config";
else if( is_file(__DIR__.'/.cloudsync.config') ) $config = __DIR__.'/.cloudsync.config';

if( !$type || !$name || !is_dir($path) || !is_file($config) ){

	if( !is_file($config) ) echo "Config file not found\n\n";

	echo "Call: ./cloudsync.php [OPTION]\n\n";
	echo "Required arguments for long options are also required for short options.\n";
	echo "    -b, --backup=<path>                            Create or refresh backup of <path>\n";
	echo "    -r, --restore=<path>                           Restore backup into <path>\n";
	echo "    -c, --clean=<path>                             Repair 'cloudsync*.cache' file and put leftover file into <path>\n";
	echo "\n";
	echo "    -n, --name=<name>                              Backup name\n";
	echo "        --config=<path>                            Config file path. Default is /etc/cloudsync, ~/.cloudsync.config\n";
	echo "        --keeplinks=<internal>|<all>|<none>        How to handle symbolic links\n";
	echo "                                                        internal - keeps only links where the target is part of the current backup structure - (default)\n";
	echo "                                                        all - keep all symbolic links\n";
	echo "                                                        none - convert all symbolic links to real files or folders\n";	
	echo "        --duplicate=<stop>|<update>|<rename>       Behavior on existing files:\n";
	echo "                                                        stop - stop immediately - (default)\n";
	echo "                                                        update - replace file\n";
	echo "                                                        rename - extend the name with an autoincrement number\n";
	echo "        --nopermissions                            Don't restore permission, group and owner attributes\n";
	echo "        --nocache                                  Don't use 'cloudsync*.cache' file (much slower)\n";
	echo "\n";
	echo "    -h, --help                                     Show this help\n";
	echo "\n";
	
	exit(0);
}

Logger::setLevel(2);

try{
	Helper::checkPHPVersion("5.3.0");
	
	require_once $config;

	if(!isset($REMOTE_CLIENT_TOKEN)) $REMOTE_CLIENT_TOKEN=__DIR__.'/.cloudsync.token';
	$CACHE_FILE = (isset($CACHE_FILE_BASE) ? $CACHE_FILE_BASE : __DIR__)."/.cloudsync_".$name.".cache";
	
	$remoteConnection = new ConnectionGoogleDrive( $REMOTE_CLIENT_ID, $REMOTE_CLIENT_SECRET, $REMOTE_CLIENT_TOKEN, $REMOTE_DIR."/".$name );
	$localConnection = new ConnectionFilesystem( $path );
	$structure = new Structure($localConnection,$remoteConnection,$PASSPHRASE,$duplicate,$keeplinks,$nopermissions);

	$start = time();
	switch( $type ){
		case "backup":
			backup( $structure, $CACHE_FILE, $MAX_CACHE_FILE_AGE, $nocache );
			break;
		case "restore":
			restore( $structure );
			break;
		case "clean":
			clean( $structure, $CACHE_FILE );
			break;
	}
	$end = time();
	Logger::log( 1, "\nruntime: ".($end-$start)." seconds" );
}
catch( Exception $e ){

    Logger::log( 1, "ERROR: ".$e->getMessage() );
}

function clean( $structure, $cacheFile ){

	Logger::log( 1, "load structure from server" );
	$structure->buildStructureFromRemoteConnection();

	Logger::log( 1, "start clean" );
	$structure->clean();
	$structure->saveToStructureFile( $cacheFile );
}

function restore( $structure ){

	Logger::log( 1, "load structure from server" );
	$structure->buildStructureFromRemoteConnection();

	Logger::log( 1, "start restore" );
	$structure->restore(false);
	$structure->saveToStructureFile( $cacheFile );
}

function backup( $structure, $cacheFile, $maxCacheAge, $nocache ){

	if( is_file( $cacheFile ) && !$nocache ){
	
		if( time() - filectime($cacheFile) > ($maxCacheAge * 24 * 60 * 60) ){

			Logger::log(1,"state file is older than ".$maxCacheAge." days. force a server refresh.");
			unlink( $cacheFile );
			$structure->buildStructureFromRemoteConnection();
		}
		else{

			Logger::log( 1, "load structure from cache" );
			$structure->buildStructureFromFile( $cacheFile );
		}
	}
	else{

		Logger::log( 1, "load structure from server" );
		$structure->buildStructureFromRemoteConnection();
	}

	Logger::log( 1, "start sync" );
	$structure->backup(false);
	$structure->saveToStructureFile( $cacheFile );
}
