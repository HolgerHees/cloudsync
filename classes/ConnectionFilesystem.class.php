<?php

class ConnectionFilesystem {

	private static $gid_state = array();
	private static $uid_state = array();

	private $localPath;
	
	public function __construct( $path ){

		$this->localPath = "/".trim($path,"/");
		
		Helper::checkCMD("gpg2 --help","gpg2");
	}
	
	public function prepareUpload( $structure, $item, $duplicateFlag ){
	
		if( $duplicateFlag != Structure::DUPLICATE_RENAME ) return;
	
		$path = $this->localPath."/".$item->getPath();
	
		if( $this->_path_exists($path) && $duplicateFlag == Structure::DUPLICATE_RENAME ){
			
			$i = 0;
			while( $this->_path_exists($path.".".$i) ){
				$i++;
			}
			
			$path .= ".".$i;
				
			$item->setName(basename($path));
		}
	}
	
	private function _path_exists( $path ){
	
		return ( file_exists($path) || @readlink( $path ) !== false );
	}

	public function upload( $structure, $item, $duplicateFlag, $nopermissions, $passphrase ){
	
		$path = $this->localPath."/".$item->getPath();
	
		if( $this->_path_exists($path) ){
		
			if( $duplicateFlag != Structure::DUPLICATE_UPDATE ) throw new Exception( "item '".$item->getPath()."' exists");
	
			if( (!$item->isType( Item::FOLDER ) || !is_dir($path)) && !unlink($path) ){
				throw new Exception("can't clear ".$item->getTypeName()." on '".$path."'");
			}
		}
		
		if( $item->isType( Item::FOLDER ) ){
		
			if( !is_dir($path) && !mkdir($path,$item->getPermissions(),true) ){
				throw new Exception("can't create folder '".$path."'");
			}
		}
		else {
		
			if( !is_dir( dirname( $path ) ) ){
			
				if( !mkdir( dirname( $path ), !$nopermissions && $item->getParent() ? $item->getParent()->getPermissions() : 0700, true ) ){
					throw new Exception("can't create folder '".dirname( $path )."'");
				}
			}
			
			if( $item->isType( Item::LINK ) ){

				$text = $structure->getRemoteEncryptedBinary( $item );
				$link = $this->_decryptData( $text, false, $passphrase );
				symlink( $link, $path );
			}
			else if( $item->isType( Item::FILE ) ){
			
				$text = $structure->getRemoteEncryptedBinary( $item );
				$this->_decryptData( $text, $path, $passphrase );
			
				if( !$nopermissions && !chmod($path,$item->getPermissions()) ){
					throw new Exception("can't set permission '".$item->getPermissions()."' on '".$path."'");
				}
			}
			else{
				throw new Exception( "can't create ".$item->getTypeName()."' on '".$path."'");
			}
		}
		
		if( !$nopermissions && !$item->isType( Item::LINK ) ){
			if( !chgrp($path,intval($item->getGID())) ){
				if( !isset($this->gid_state[$item->getGID()])) {
					if( !posix_getgrgid($item->getGID() )){
						$this->gid_state[$item->getGID()]=true;
						Logger::log( 1, "group with id '".$item->getGID()."' not exists");
					}
					else{
						throw new Exception("can't set group '".$item->getGID()."' on file '".$path."'");
					}
				}
			}
		
			if( !chown($path,intval($item->getUID())) ){
				if( !isset($this->uid_state[$item->getUID()])) {
					if( !posix_getpwuid($item->getUID() )){
						$this->uid_state[$item->getUID()]=true;
						Logger::log( 1, "user with id '".$item->getUID()."' not exists");
					}
					else{
						throw new Exception("can't set user '".$item->getUID()."' on file '".$path."'");
					}
				}
			}
		}
	}

	public function update( $structure, $item ){
	    // TODO
	    throw new Exception("not implemented");
	}

	public function remove( $structure, $item ){
	    // TODO
	    throw new Exception("not implemented");
	}

	public function getEncryptedBinary( $item, $passphrase ){

		if( $item->isType( Item::LINK ) ){
		    return $this->_encryptData( readlink( $this->localPath."/".$item->getPath() ), $passphrase );
		}
		else if( $item->isType( Item::FILE ) ){
		    return shell_exec('gpg2 --batch --passphrase "'.$passphrase.'" --output - --symmetric "'.$this->localPath."/".$item->getPath().'"');
		}
		else{
		    return false;
		}
		
	}

	public function decryptText( $text, $passphrase ){

		$text = str_replace("_","/",$text);
		$text = base64_decode($text);
		
		return $this->_decryptData( $text, false, $passphrase );
	}
	
	public function _decryptData( $text, $path, $passphrase ){

		$descriptorspec = array(
			0 => array("pipe", "r"),
			1 => array("pipe", "w"),
			2 => array("pipe", "w")
		);
		
		$cmd = 'gpg2 --batch --passphrase "'.$passphrase.'" --output ';
		if( $path ) $cmd .= '"'.$path.'"';
		else $cmd .= '-';
		$cmd .= ' --decrypt';
		
		$pipes = false;
		$process = proc_open($cmd, $descriptorspec, $pipes);
		                
		if(is_resource($process)) {
		                        
			fwrite($pipes[0], $text);
			fclose($pipes[0]);
		                                
			$output = stream_get_contents($pipes[1]);
			$stderr = stream_get_contents($pipes[2]);
		                                        
			fclose($pipes[1]);
			fclose($pipes[2]);

			$retval = proc_close($process);
			
			if( $retval !== 0 ){
			
				if( $path ) throw new Exception("can't decrypt item '".$path."'. gpg error: ".$stderr);
				else throw new Exception("can't decrypt text. gpg error: ".$stderr);
			}
			
			//echo ":".$output.":\n";
			
			return $output;
		}
		else{
			throw new Exception("can't run gpg");
		}

		//$text = str_replace("_","/",$text);
		//$text = shell_exec('echo "'.$text.'" | base64 --decode --wrap=0 | gpg2 --batch --passphrase "'.$gpg_passphrase.'" --output - --decrypt 2> /dev/null');
		//$text = trim($text);
		//return $text;
	}
		
	public function encryptText( $text, $passphrase ){
	
		$text = $this->_encryptData( $text, $passphrase );
		$text = base64_encode($text);
		$text = str_replace("/","_",$text);
		return $text;
	}
	
	public function _encryptData( $text, $passphrase ){

		$descriptorspec = array(
			0 => array("pipe", "r"),
			1 => array("pipe", "w"),
			2 => array("pipe", "w")
		);
		                
		$pipes = false;
		$process = proc_open('gpg2 --batch --passphrase "'.$passphrase.'" --output - --symmetric', $descriptorspec, $pipes);
		                
		if(is_resource($process)) {
		                        
			fwrite($pipes[0], $text);
			fclose($pipes[0]);
		                                
			$output = stream_get_contents($pipes[1]);
			$stderr = stream_get_contents($pipes[2]);
		                                        
			fclose($pipes[1]);
			fclose($pipes[2]);

			$retval = proc_close($process);
			
			if( $retval !== 0 ){
			
				throw new Exception("can't encrypt text. gpg error: ".$stderr);
			}
			
			return $output;
		}
		else{
			throw new Exception("can't run gpg");
		}

		//$text = shell_exec('echo "'.$text.'" | gpg2 --batch --passphrase "'.$gpg_passphrase.'" --output - --symmetric | base64 --wrap=0');
		//$text = str_replace("/","_",$text);
		//return $text;
	}

	public function readFolder( $structure, $item, $keeplinks ){

		$currentPath = $this->localPath.( $item->getPath() == "" ? "" : "/".$item->getPath() );

		$child_items = array();

		if( $dh  = opendir($currentPath) ){

			while (false !== ($filename = readdir($dh))) {

				if( $filename == '.' || $filename == '..' ) continue;

				$_currentPath = $currentPath."/".$filename;

				$stats = stat($_currentPath);
				$type = Item::UNKNOWN;
				if( is_link($_currentPath) ){
				
					$target = readlink( $_currentPath );
					$char = substr($target,0,1);
					if( $char != '/' ){
						if( $char != '.' ) $target = './'.$target;
						$target = dirname($_currentPath)."/".$target;
					}
					$target = realpath( $target );
					
					switch( $keeplinks ){
						case Structure::LINK_ALL:
							$type = Item::LINK;
							break;
						case Structure::LINK_INTERNAL:
							if( strpos( $target, $this->localPath ) === 0 ){
								$type = Item::LINK;
								break;
							}
						default:
							$_currentPath = $target;
							break;
					}
				}
				if( $type == Item::UNKNOWN ){
					if( is_dir($_currentPath) ) $type = Item::FOLDER;
					else if( is_file($_currentPath) ) $type = Item::FILE;
				}
				
				$child_items[] = new Item($filename,null, $type,$stats['size'],$stats['mtime'],$stats['ctime'],$stats['gid'],$stats['uid'],fileperms($_currentPath));
			}
			closedir($dh);
		}

		return $child_items;
	}
}
