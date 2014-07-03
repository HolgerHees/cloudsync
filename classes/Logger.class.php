<?php

class Logger {

	private static $level;

	public static function setLevel( $level ){
		self::$level = $level;
	}

	public static function log( $level, $message ){
		if( $level > self::$level ) return;
		echo $message."\n";
	}
} 
