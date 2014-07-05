<?php

class Item {

	const UNKNOWN = 0;
	const FOLDER = 1;
	const FILE = 2;
	const LINK = 3;

	private $parent;

	private $path;
	private $remoteIdentifier;
	private $realPath;
	private $type;
	private $filesize;
	private $modifytime;
	private $creationtime;
	private $gid;
	private $uid;
	private $permissions;
	
	private $children = false;

	public function __construct($name, $remoteIdentifier, $type, $filesize, $modifytime, $creationtime, $gid, $uid, $permissions ){

		$this->name = $name;
		$this->remoteIdentifier = $remoteIdentifier;
		$this->type = $type;
		$this->filesize = $filesize;
		$this->modifytime = $modifytime;
		$this->creationtime = $creationtime;
		$this->gid = $gid;
		$this->uid = $uid;
		$this->permissions = $permissions;
		
		if( $this->type == Item::FOLDER ) $this->children = array();
	}
	
	public static function getDummyRoot(){
	    return new Item("","",Item::FOLDER,-1,-1,-1,-1,-1,-1);
	}

	public static function fromArray($values){
		return new Item(basename($values[0]),$values[1],$values[2],$values[3],$values[4],$values[5],$values[6],$values[7],$values[8]);
	}

	public function toArray(){
		return array($this->getPath(),$this->remoteIdentifier,$this->type,$this->filesize,$this->modifytime,$this->creationtime,$this->gid,$this->uid,$this->permissions);
	}
	
	public static function fromMetadata( $name, $remoteIdentifier, $metadata){
		return new Item( $name, $remoteIdentifier, $metadata[0], $metadata[1], $metadata[2], $metadata[3], $metadata[4], $metadata[5], $metadata[6] );
	}

	public function getMetadata(){
		return array( $this->type, $this->filesize, $this->modifytime, $this->creationtime, $this->gid, $this->uid, $this->permissions );
	}

	public function setParent( $parent ){
		$this->parent = $parent;
	}
	
	public function getParent(){
		return $this->parent;
	}
	
	public function setRemoteIdentifier( $remoteIdentifier ){
		$this->remoteIdentifier = $remoteIdentifier;
	}
	
	public function addChild( $child ){
		$this->children[$child->getName()] = $child;
		$child->setParent($this);
	}
	
	public function getChildByName( $name ){
		return isset($this->children[$name]) ? $this->children[$name] : null;
	}

	public function getChildren(){
		return $this->children;
	}

	public function getTypeName(){

		switch( $this->type ){
		
			case 1:
				return "folder";
			case 2:
				return "file";
			case 3:
				return "link";
			default:
				return "unknown";
		}
	}

	public function isTypeChanged( $item ){
		if( $this->type != $item->type ) return true;
		return false;
	}

	public function isFiledataChanged( $item ){
		if( $this->filesize != $item->filesize ) return true;
		if( $this->modifytime != $item->modifytime ) return true;
		if( $this->creationtime != $item->creationtime ) return true;
		return false;
	}

	public function isMetadataChanged( $item ){
		if( $this->filesize != $item->filesize ) return true;
		if( $this->modifytime != $item->modifytime ) return true;
		if( $this->creationtime != $item->creationtime ) return true;
		if( $this->gid != $item->gid ) return true;
		if( $this->uid != $item->uid ) return true;
		if( $this->permissions != $item->permissions ) return true;
		return false;
	}
	
	public function update( $item ){
		$this->type = $item->type;
		$this->filzesize = $item->filesize;
		$this->modifytime = $item->modifytime;
		$this->creationtime = $item->creationtime;
		$this->gid = $item->gid;
		$this->uid = $item->uid;
		$this->permissions = $item->permissions;
	}
	
	public function getPath(){
		$path = "";
		if( $this->parent ) $path .= $this->parent->getPath();
		if( $path != "" ) $path .= "/";
		$path .= $this->name;
		return $path;
	}
	
	public function getName(){
		return $this->name;
	}
	
	public function setName($name){
		$this->name = $name;
	}

	public function getRemoteIdentifier(){
		return $this->remoteIdentifier;
	}

	public function isType( $type ){
		return $this->type == $type;
	}
	
	public function getGID(){
		return $this->gid;
	}
	
	public function getUID(){
		return $this->uid;
	}

	public function getPermissions(){
		return $this->permissions;
	}
} 
