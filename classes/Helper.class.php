<?php

class Helper {

	public static function checkPHPVersion( $version ){
		if (version_compare(PHP_VERSION, $version) < 0) {
			throw new Exception("PHP version ".$version." or higher is required.");
		}
	}

	public static function checkCMD( $cmd, $name ){
		exec( $cmd.' >& /dev/null && echo "Found" || echo "Not Found"', $output );
		if ( $output[0] != "Found" ) {
			throw new Exception($name." binary is not found.");
		}
	}
} 
