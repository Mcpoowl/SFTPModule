#(S)FTP Module for Mendix 5.8.1 and up

## Description
The (S)FTP module used the Jsch and Apache Commons libraries to allow you to browse (S)FTP servers from within your application. The module allows for downloading and uploading files, downloading an entire folder at once as a ZIP file, and creating new folders.

## Typical usage scenario
Useful if you want to connect to an (S)FTP server from within your Mendix application!

## Features and limitations
####Features:
* FTP
* SFTP
* Browse folders
* Download / upload file
* Download all files in folder as ZIP
* Create new folder
* Rename files and folders
* Remove files

###Limitations:
* Currently FTPS is not supported, although this option is selectable. 

## Dependencies
* Mendix 5.8.1 and up

## Installation
* Import the module and look at the _README microflow in the _USEME folder.

## Configuration
Add the configuration (IVK_ShowConfiguration) and document overview (IVK_ShowDocumentOverview) microflows to your navigation. If you have seperate users with their own home folder, add the IVK_ShowUserCredentials to the navigation as well.
Add the module roles to your existing user roles ( Admin is for the configuration, and User has the ability to 'use' the FTP connection )
Edit the configuration with the information of  your (S)FTP server
When the UserCredentials microflow is added to the navigation, a user has the ability to overrule the username, password and homedirectory used to connect to the server with.
In the case that you're using an SFTP server, make sure that you have private.key and a known_hosts file is present in the 'resources/SFTP' folder

## Known bugs
None, but let us know if you find any!