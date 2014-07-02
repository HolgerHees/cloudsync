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
	
		if( $duplicateFlag != "rename" ) return;
	
		$path = $this->localPath."/".$item->getPath();
	
		if( file_exists($path) && $duplicateFlag == "rename" ){
			
			$i = 0;
			while( file_exists($path.".".$i) ){
				$i++;
			}
			
			$path .= ".".$i;
				
			$item->setName(basename($path));
		}
	}

	public function upload( $structure, $item, $duplicateFlag, $passphrase ){
	
		$path = $this->localPath."/".$item->getPath();
	
		if( file_exists($path) && $duplicateFlag != "update" ) throw new Exception( "item '".$item->getPath()."' exists");
	
		if( $item->isType( Item::FOLDER ) ){
		
			if( !is_dir($path) && !mkdir($path,$item->getPermissions(),true) ){
			
				throw new Exception("can't create folder '".$path."'");
			}
		}
		else{
		
			if( !is_dir( dirname( $path ) ) ){
			
				if( !mkdir( dirname( $path ), $item->getParent() ? $item->getParent()->getPermissions() : 0700, true ) ){
				
					throw new Exception("can't create folder '".dirname( $path )."'");
				}
			}
			
			$this->_writeDecrypted($structure,$item,$path,$passphrase);
			
			if( !chmod($path,$item->getPermissions()) ){
			
				throw new Exception("can't set permission '".$item->getPermissions()."' on file '".$path."'");
			}
		}
		
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

	public function update( $structure, $item ){
	}

	public function remove( $structure, $item ){
	}

	public function decryptText($text, $passphrase ){

		$descriptorspec = array(
			0 => array("pipe", "r"),
			1 => array("pipe", "w"),
			2 => array("pipe", "w")
		);
		                
		$pipes = false;
		$process = proc_open('gpg2 --batch --passphrase "'.$passphrase.'" --output - --decrypt', $descriptorspec, $pipes);
		                
		if(is_resource($process)) {
		                        
			$text = str_replace("_","/",$text);
			$text = base64_decode($text);
			
			fwrite($pipes[0], $text);
			fclose($pipes[0]);
		                                
			$output = stream_get_contents($pipes[1]);
			$stderr = stream_get_contents($pipes[2]);
		                                        
			fclose($pipes[1]);
			fclose($pipes[2]);

			$retval = proc_close($process);
			
			if( $retval !== 0 ){
			
				throw new Exception("can't decrypt text. gpg error: ".$stderr);
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
		
	public function encryptText($text, $passphrase){

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
			
			$output = base64_encode($output);
			$output = str_replace("/","_",$output);
			
			return $output;
		}
		else{

			throw new Exception("can't run gpg");
		}

		//$text = shell_exec('echo "'.$text.'" | gpg2 --batch --passphrase "'.$gpg_passphrase.'" --output - --symmetric | base64 --wrap=0');
		//$text = str_replace("/","_",$text);
		//return $text;
	}

	public function readEncryptedFile( $item, $gpg_passphrase ){

		/*$file = fopen($this->localPath."/".$item->getPath(), "rb");
		
		$descriptors = array(
			0 => $file,  // stdin is a pipe that the child will read from
			1 => array("pipe", "w"),  // stdout is a pipe that the child will write to
			2 => array("pipe", "w") // stderr is a file to write to
		);
		$process = proc_open('gpg2 --batch --passphrase "'.$gpg_passphrase.'" --output - --symmetric', $descriptors, $pipes, null, null, array('bypass_shell' => true));
		if (is_resource($process)) {

			stream_set_blocking($pipes[1], 0);
			fclose($file);
			
			$encryptedData = stream_get_contents($pipes[1]);
			fclose($pipes[1]);
			
			$error = stream_get_contents($pipes[2]);
			fclose($pipes[2]);

			$return = proc_close($process);
			
			return $encryptedData;
		}*/
		return shell_exec('gpg2 --batch --passphrase "'.$gpg_passphrase.'" --output - --symmetric "'.$this->localPath."/".$item->getPath().'"');
	}

	public function _writeDecrypted( $structure, $item, $path, $passphrase ){
	
		$text = $structure->readRemoteEncryptedFile( $item );
		
		$descriptorspec = array(
			0 => array("pipe", "r"),
			1 => array("pipe", "w"),
			2 => array("pipe", "w")
		);
		                
		if( is_file($path) ) unlink($path);
		
		$pipes = false;
		$process = proc_open('gpg2 --batch --passphrase "'.$passphrase.'" --output "'.$path.'" --decrypt', $descriptorspec, $pipes);
		                
		if(is_resource($process)) {
		                        
			fwrite($pipes[0], $text);
			fclose($pipes[0]);
		                                
			$output = stream_get_contents($pipes[1]);
			$stderr = stream_get_contents($pipes[2]);
		                                        
			fclose($pipes[1]);
			fclose($pipes[2]);

			$retval = proc_close($process);
			
			if( $retval !== 0 ){
			
				throw new Exception("can't decrypt item '".$item->getPath()."'. gpg error: ".$stderr);
			}
		}
		else{

			throw new Exception("can't run gpg");
		}
	}

	public function readFolder( $structure, $item ){

		$currentPath = $this->localPath.( $item->getPath() == "" ? "" : "/".$item->getPath() );

		$child_items = array();

		if( $dh  = opendir($currentPath) ){

			while (false !== ($filename = readdir($dh))) {

				if( $filename == '.' || $filename == '..' ) continue;

				$_currentPath = $currentPath."/".$filename;

				$stats = stat($_currentPath);
				$type = Item::OTHER;
				if( is_dir($_currentPath) ) $type = Item::FOLDER;
				else if( is_file($_currentPath) ) $type = Item::FILE;

				$child_items[] = new Item($filename,null, $type,$stats['size'],$stats['mtime'],$stats['ctime'],$stats['gid'],$stats['uid'],substr(sprintf('%o', fileperms($_currentPath)), -4));				
			}
			closedir($dh);
		}

		return $child_items;
	}
}

 
