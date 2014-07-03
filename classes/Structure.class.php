<?php

class Structure {

	const DUPLICATE_STOP = "stop";
	const DUPLICATE_UPDATE = "update";
	const DUPLICATE_RENAME = "rename";

	const LINK_INTERNAL = "internal";
	const LINK_NONE = "none";
	const LINK_ALL = "all";

	private $localConnection;
	private $remoteConnection;
	private $passphrase;

	private $root;
	private $duplicates;
	private $duplicateFlag;
	private $keeplinks;
	private $nopermissions;

	private $processed;

	public function __construct($localConnection,$remoteConnection,$passphrase, $duplicate, $keeplinks, $nopermissions ){

		$this->structure = array();
		$this->localConnection = $localConnection;
		$this->remoteConnection = $remoteConnection;
		$this->passphrase = $passphrase;
		$this->duplicateFlag = $duplicate;
		$this->keeplinks = $keeplinks;
		$this->nopermissions = $nopermissions;
		
		$this->root = Item::getDummyRoot();
	}

	public function buildStructureFromFile( $structureFilePath ){
	
		$mapping = array( "" => $this->root );
	
		$fh = fopen($structureFilePath, "r");
		if ($fh) {

			//$this->timestamp = fgets($fh,4096);

			while(($values = fgetcsv($fh, 4096)) !== false) {

				if( empty($values) ) continue;
		
				$item = Item::fromArray($values);
				
				$parentPath = strlen($values[0]) == strlen($item->getName()) ? "" : dirname($values[0]);
				$mapping[$values[0]] = $item;
				
				$parentItem = $mapping[$parentPath]->addChild($item);
			}
			fclose($fh);
		}
		else{
			throw new Exception("can not open file '".$currentPath."'");
		}
	}

	public function buildStructureFromRemoteConnection(){
		$this->_walkRemoteStructure( $this->root );
	}

	private function _walkRemoteStructure( $parentItem ){

		$childItems = $this->remoteConnection->readFolder( $this, $parentItem, $this->keeplinks ) ;
		
		foreach( $childItems as $childItem ) {
		
			if( $existingChildItem = $parentItem->getChildByName($childItem->getName()) ){
			
				if( $existingChildItem->getModifyTime() < $childItem->getModifyTime() ){
					$parentItem->addChild($childItem);
					$this->duplicates[] = $existingChildItem;
				}
				else{
					$this->duplicates[] = $childItem;
				}
			}
			else{
				$parentItem->addChild($childItem);
			}
			
			if( $childItem->isType(Item::FOLDER) ){
				$this->_walkRemoteStructure( $childItem );
			}
		}
	}

	public function clean(){
	
		if( count( $this->duplicates ) > 0 ){
		
			$list = array();
			foreach( $this->duplicates as $item ){
				$list = array_merge( $list, $this->_flatRecursiveChildren( $item ) );
			}
			foreach( $list as $item ){
				$this->localConnection->prepareUpload( $this, $item, $this->duplicateFlag );
				Logger::log( 2, "restore ".$item->getTypeName()." '".$item->getPath()."'" );
				$this->localConnection->upload( $this, $item, $this->duplicateFlag, $this->nopermissions, $this->passphrase );
			}

			foreach( array_reverse($list) as $item ){
				Logger::log( 2, "clean ".$item->getTypeName()." '".$item->getPath()."'" );
				//$this->remoteConnection->upload( $this, $item );
			}
		}
	}
	
	public function restore( $dryRun ){
		$status_r = $this->_restoreRemoteStructure( $this->root, $dryRun );
	}
	
	private function _restoreRemoteStructure( $item, $dryRun ){
	
		foreach( $item->getChildren() as $child ){
		
			$this->localConnection->prepareUpload( $this, $child, $this->duplicateFlag );
			Logger::log( 2, "restore ".$child->getTypeName()." '".$child->getPath()."'" );
			if( !$dryRun ) {
				$this->localConnection->upload( $this, $child, $this->duplicateFlag, $this->nopermissions, $this->passphrase );
			}
			
			if( $child->isType( Item::FOLDER ) ){
				$this->_restoreRemoteStructure( $child, $dryRun );
			}
		}
	}
	
	public function backup( $dryRun ){
	
		if( count( $this->duplicates ) > 0 ){
		
			$message = "find ".count($this->duplicates)." duplicate items:\n\n";
			$list = array();
			foreach( $this->duplicates as $item ){
				$list = array_merge( $list, $this->_flatRecursiveChildren( $item ) );
			}
			foreach( $list as $item ){
				$message .= "  ".$item->getRemoteIdentifier()." - ".$item->getPath()."\n";
			}
			$message .= "\ntry to run with '--clean=<path>'";
			
			throw new Exception($message);
		}
		
		$status_r = array(
		    'create' => 0,
		    'update' => 0,
		    'remove' => 0,
		    'skip' => 0
		);
		
		$status_r = $this->_backupLocalStructure( $this->root, $dryRun, $status_r );
				
		$total = $status_r['create'] + $status_r['update'] + $status_r['skip'];
		Logger::log(1,"total items: ".$total );
		Logger::log(1,"created items: ".$status_r['create'] );
		Logger::log(1,"updated items: ".$status_r['update'] );
		Logger::log(1,"removed items: ".$status_r['remove'] );
		Logger::log(1,"skipped items: ".$status_r['skip'] );
	}

	private function _backupLocalStructure( $remoteParentItem, $dryRun, $status_r ){

		$unusedRemoteChildItems = $remoteParentItem->getChildren();
		$localChildItems = $this->localConnection->readFolder( $this, $remoteParentItem, $this->keeplinks ) ;
		
		foreach( $localChildItems as $localChildItem ) {
		
			if( !($remoteChildItem = $remoteParentItem->getChildByName( $localChildItem->getName() ) ) ){
				$remoteChildItem = $localChildItem;
				$remoteParentItem->addChild($remoteChildItem);
				Logger::log( 2, "create ".$remoteChildItem->getTypeName()." '".$remoteChildItem->getPath()."'" );
				if( !$dryRun ){
					$this->remoteConnection->upload( $this, $remoteChildItem );
				}
				$status_r['create']++;
			}
			else{

				//echo $item->getFileSize() ." ". $this->structure[$key]->getFileSize()."\n";
				//echo $item->getModifyTime() ." ". $this->structure[$key]->getModifyTime()."\n";

				// check filesize
				if( $localChildItem->isTypeChanged( $remoteChildItem ) ){
					Logger::log( 2, "remove ".$remoteChildItem->getTypeName()." '".$remoteChildItem->getPath()."'" );
					if( !$dryRun ) $this->remoteConnection->remove( $this, $remoteChildItem );
					$status_r['remove']++;
					
					$remoteChildItem = $localChildItem;
					$remoteParentItem->addChild($remoteChildItem);
					Logger::log( 2, "create ".$remoteChildItem->getTypeName()." '".$remoteChildItem->getPath()."'" );
					if( !$dryRun ) $this->remoteConnection->upload( $this, $localChildItem );
					$status_r['create']++;
				}
				// check filesize and modify time
				else if( $localChildItem->isMetadataChanged( $remoteChildItem ) ){
					$remoteChildItem->update( $localChildItem );
					Logger::log( 2, "update ".$remoteChildItem->getTypeName()." '".$remoteChildItem->getPath()."'" );
					if( !$dryRun ) $this->remoteConnection->update( $this, $remoteChildItem );
					$status_r['update']++;
				}
				else{
					$status_r['skip']++;
				}
			}
			
			unset( $unusedRemoteChildItems[ $remoteChildItem->getName() ] );

			if( $remoteChildItem->isType(Item::FOLDER) ){
				$status_r = $this->_backupLocalStructure( $remoteChildItem, $dryRun, $status_r );
			}
		}
		
		foreach( $unusedRemoteChildItems as $item ){
			Logger::log( 2, "remove ".$remoteChildItem->getTypeName()." '".$item->getPath()."'" );
			if( !$dryRun ) $this->remoteConnection->remove( $this, $item );
			$status_r['remove']++;
		}		
		
		return $status_r;
	}

	private function _flatRecursiveChildren( $parentItem ){
	
		$list = array( $parentItem );
	
		if( $parentItem->isType(Item::FOLDER) ){
			foreach( $parentItem->getChildren() as $childItem ){
				$list = array_merge( $list, $this->_flatRecursiveChildren( $childItem ) );
			}
		}
		
		return $list;
	}

	public function saveToStructureFile( $structureFilePath ){

		if( $fp = fopen($structureFilePath, 'w') ){
			//fputs($fp, $this->timestamp."\n");
			$this->_saveToStructureFile( $fp, $this->root );
			fclose($fp);
		}
		else{
			throw new Exception("can not create file '".$structureFilePath."'");
		}
	}
	
	private function _saveToStructureFile( $fp, $parentItem ){
	
		foreach( $parentItem->getChildren() as $child ){
			fputcsv($fp, $child->toArray());
			if( $child->isType(Item::FOLDER) ) $this->_saveToStructureFile( $fp, $child );
		}
	}

	public function getItems(){
		return $this->structure;
	}
	
	public function getItem( $path ){
		return isset($this->structure[$path]) ? $this->structure[$path] : null;
	}
	
	public function getRootItem(){
		return $this->root;
	}
	
	public function decryptText($text){
		return $this->localConnection->decryptText($text,$this->passphrase);
	}

	public function encryptText($text){
		return $this->localConnection->encryptText($text,$this->passphrase);
	}
	public function getLocalEncryptedBinary($item){
		return $this->localConnection->getEncryptedBinary($item,$this->passphrase);
	}

	public function getRemoteEncryptedBinary($item){
		return $this->remoteConnection->get($item);
	}
}

 
