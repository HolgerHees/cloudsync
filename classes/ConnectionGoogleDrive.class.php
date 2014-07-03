<?php

set_include_path(__dir__."/google-api-php-client-master/src/" . PATH_SEPARATOR . get_include_path());
require_once 'Google/Client.php';
require_once 'Google/Service/Drive.php';

class ConnectionGoogleDrive {

	private $cacheFiles;
	private $basePath;
	
	private $client;
	private $service;
	
	private $rootDriveItem;

	const FOLDER = 'application/vnd.google-apps.folder';

	public function __construct( $clientId, $clientSecret, $clientTokenFile, $basePath ){

		$this->cacheFiles = array();
		$this->basePath = trim($basePath, '/');
		$this->clientTokenFile = $clientTokenFile;
		
		if( empty( $clientId ) ) throw new Exception("Google Client ID is missing");
		if( empty( $clientSecret ) ) throw new Exception("Google Client Secret is missing");

		$this->client = new Google_Client();
		$this->client->setApplicationName("Backup");
		$this->client->setClientId($clientId);
		$this->client->setClientSecret($clientSecret);
		$this->client->setRedirectUri("urn:ietf:wg:oauth:2.0:oob");
		$this->client->setScopes(array(Google_Service_Drive::DRIVE));
		$this->client->setAccessType('offline');
		
		$clientToken = is_file($this->clientTokenFile) ? file_get_contents($this->clientTokenFile) : false;

		if( empty( $clientToken ) ){
		
			$url = $this->client->createAuthUrl();
		
			Logger::log(1,"please visit:\n\n".$url."\n");
			Logger::log(1,"please enter the auth code:");
			$authCode = trim(fgets(STDIN));

			$response = $this->client->authenticate("$authCode");
			$clientToken = $this->client->getAccessToken();
			file_put_contents($clientTokenFile, $clientToken );
			Logger::log(1,"\nclient token stored in '".$this->clientTokenFile."'\n");
		}
		else{
			$this->client->setAccessToken($clientToken);
			
			if( $this->client->isAccessTokenExpired() ){
			    
				$clientTokenArray = json_decode($clientToken);
				$this->client->refreshToken($clientTokenArray->refresh_token);
				file_put_contents($clientTokenFile, $this->client->getAccessToken() );
				Logger::log(1,"refreshed client token stored in '".$this->clientTokenFile."'\n");
			}
		}
	}
	
	public function _init( $rootItem ){

		$this->service = new Google_Service_Drive( $this->client );
		$rootItem->setRemoteIdentifier($this->_getDriveRoot()->getId());
	}

	public function upload( $structure, $item ){
	
		if( !$this->service ) $this->_init( $structure->getRootItem() );

		$driveItem = new Google_Service_Drive_DriveFile();
		$driveItem->setTitle( $structure->encryptText($item->getName()) );
		$parentDriveItem = $this->_getDriveItem($item->getParent());
		$parentReference = new Google_Service_Drive_ParentReference();
		$parentReference->setId($parentDriveItem->getId());
		$driveItem->setParents(array($parentReference));
		$params = $this->_prepareDriveItem($driveItem,$item,$structure);
		$_driveItem = $this->service->files->insert($driveItem,$params);
		if( !$_driveItem ) throw new Exception("Could not create item '".$item->getPath()."'");
		$this->cacheFiles[$_driveItem->getId()] = $_driveItem;
		$item->setRemoteIdentifier($_driveItem->getId());
	}

	public function update( $structure, $item ){

		if( !$this->service ) $this->_init( $structure->getRootItem() );

		$driveItem = $this->_getDriveItem( $item );
		$params = $this->_prepareDriveItem($driveItem,$item,$structure);
		$_driveItem = $this->service->files->update($driveItem->getId(), $driveItem, $params);
		if( !$_driveItem ) throw new Exception("Could not update item '".$item->getPath()."'");
		$this->cacheFiles[$_driveItem->getId()] = $_driveItem;
	}

	public function remove( $structure, $item ){

		if( !$this->service ) $this->_init( $structure->getRootItem() );

		$driveItem = $this->_getDriveItem( $item );
		$result = $this->service->files->delete($driveItem->getId());
		unset( $this->cacheFiles[$driveItem->getId()] );
	}
	
	public function get( $item ){
	
		$driveItem = $this->_getDriveItem( $item );
		if( $downloadUrl = $driveItem->getDownloadUrl() ){

			$request = new Google_Http_Request($downloadUrl, 'GET', null, null);
			$httpRequest = $this->client->getAuth()->sign($request);
			$httpResponse = $this->client->getIo()->makeRequest($httpRequest);
			if ($httpResponse->getResponseHttpCode() == 200) {
				return $httpResponse->getResponseBody();
			}
		}

		throw new Exception("Could not get item '".$item->getPath()."'");
	}

	public function readFolder( $structure, $parentItem, $keeplinks ){
	
		if( !$this->service ) $this->_init( $structure->getRootItem() );

		$child_items = array();

		$pageToken = true;
		while ($pageToken) {
			$params = array();
			if ($pageToken !== true) $params['pageToken'] = $pageToken;
			$params['q'] = "'".$parentItem->getRemoteIdentifier()."' in parents and trashed = false";
			$children = $this->service->files->listFiles($params);
			foreach ($children->getItems() as $driveChildItem) {
				$this->cacheFiles[$driveChildItem->getId()]= $driveChildItem;
				$child_items[] = $this->_prepareBackupItem( $parentItem, $driveChildItem, $structure);
			}
			$pageToken = $children->getNextPageToken();
		}

		return $child_items;
	}

	private function _prepareDriveItem( $driveItem, $item, $structure ){

		//echo $item->getModifyTime()."\n\n";
		//echo date("Y-m-d\TH:i:s.000\Z", $item->getModifyTime() )."\n\n";
		
		$driveItem->setProperties(array(
			array(
				'key' => "metadata",
				'value' => $structure->encryptText(implode($item->getMetadata(),":")),
				'visibility' => 'PRIVATE'
			)
		));
		
		$driveItem->setMimetype( $item->isType( Item::FOLDER ) ? self::FOLDER : 'application/octet-stream' );

		$params = array();
		if( ( $data = $structure->getLocalEncryptedBinary($item) ) !== false ){
			$params = array(
				'data' => $data,
				'mimeType' => 'application/octet-stream',
				'uploadType' => 'media'
			);
		}
		return $params;
	}

	private function _prepareBackupItem( $parentItem, $driveItem, $structure ){

		$metadata = array();

		foreach( $driveItem->getProperties() as $property ){
			if( $property['key']!='metadata' ) continue;
			
			$metadata = $property['value'];
			$metadata = $structure->decryptText($metadata);
			$metadata = explode(":",$metadata);
		}
			
		return Item::fromMetadata($structure->decryptText($driveItem->getTitle()), $driveItem->getId(), $metadata);
	}

	private function _getDriveItem( $item ){

		$id = $item->getRemoteIdentifier();

		if( isset($this->cacheFiles[$id]) ){

			return $this->cacheFiles[$id];
		}
		
		try{
			$driveItem = $this->service->files->get($id);
		}
		catch( Exception $e ){
			throw new Exception("couldn't find remote item '".$item->getPath()."' [".$id."]\ntry to run with --nocache");
		}
		
		if( $driveItem->getLabels()->getTrashed() ){
			throw new Exception("remote item '".$item->getPath()."' [".$id."] is trashed\ntry to run with --nocache");
		}

		$this->cacheFiles[$id] = $driveItem;
		return $driveItem;
	}

	private function _getDriveRoot(){

		$parentItem = $this->service->files->get('root');
		$this->cacheFiles[$parentItem->getId()] = $parentItem;

		$folderNames = explode('/', $this->basePath);
		foreach ($folderNames as $name) {

			$q = "title='".$name."' and '".$parentItem->getId()."' in parents and trashed = false";
			$result = $this->service->files->listFiles(array('q' => $q))->getItems();

			if( count($result)==0 ){

				$folder = new Google_Service_Drive_DriveFile();
				$folder->setTitle( $name );
				$folder->setMimeType(self::FOLDER);
				$parent = new Google_Service_Drive_ParentReference();
				$parent->setId($parentItem->getId());
				$folder->setParents(array($parent));
				$parentItem = $this->service->files->insert($folder);
				if( !$parentItem ) throw new Exception("Could not create folder '".$name."'"); 
			}
			else if( count($result) == 1 ){
				$parentItem = current($result);
			}
			else{
				throw new Exception("base path '".$this->basePath."' not unique"); 
			}
			$this->cacheFiles[$parentItem->getId()] = $parentItem;
		}

		return $parentItem;
	}
}

 
