package sftp.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import sftp.proxies.Configuration;
import sftp.proxies.Connection;
import sftp.proxies.Document;
import sftp.proxies.DownloadDirectory;
import system.proxies.FileDocument;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IFeedback;
import com.mendix.systemwideinterfaces.core.IMendixObject;




/**
 * A program demonstrates how to upload files from local computer to a remote
 * FTP server using Apache Commons Net API.
 * 
 * @author www.codejava.net
 */
public class FTPConnection {

	private static final ILogNode _logNode = Core.getLogger("(S)FTP");

		
	public static String execute(FileDocument mendixDoc, Configuration configuration, Connection connection, List<IMendixObject> SFTPFileDocumentList, IContext context, Action sftpAction ){

		boolean useUserCredentials = configuration.getuseUserCredentials(context);
		
		String user = "";
		String password = "";
		if (useUserCredentials) {
			user = connection.getFTPUsername(context);
			password = connection.getFTPPassword(context);
		} else {
			user = configuration.getUsername(context);
			password = configuration.getPassword(context);
		}
		
		String host = configuration.getHostname(context);
		Integer port = configuration.getPort(context);
		Boolean keepRemoteFile = configuration.getKeepRemoteFile(context);
		FTPClient client = new FTPClient();
		String fileName = "";
	    if (mendixDoc!=null) {
	    	fileName = mendixDoc.getName();
	    }
	    
	    String URL = connection.getCurrentDirectory(context);
	    
	    //Check if the last character of the URL is a /. If not, add it.
	    String lastChar = URL.substring(URL.length() -1);
	    if (!lastChar.equals('/')) {
	    	URL += '/';
	    }
		
		try {

			client.connect(host, port);
			client.login(user, password); 
				
			client.enterLocalPassiveMode();
			client.setFileType(FTP.BINARY_FILE_TYPE);
			if(sftpAction == Action.LS) {
				FTPFile[] fileList = client.listFiles(URL);
				
				// the commons API doesn't automatically list the '..' directory, so for us to be able to navigate to the parent dir, we have to create it ourselves.
				Document parentDoc = new Document(context);
				parentDoc.setDirectory(context, URL);
				parentDoc.setName(context, "..");
				parentDoc.setFileType(context, sftp.proxies.FileType.Folder);
				try {
					parentDoc.commit(context);
				} catch (CoreException e) {
					closeConnection(client);
					_logNode.error("File commit failed!",e);
					e.printStackTrace();
				}
				SFTPFileDocumentList.add(parentDoc.getMendixObject());
				// Now loop over all the files given from the FTP and create a Mendix object for each of them so we can view them in our application.
				for (FTPFile file : fileList) {
					Document newFileDoc = new Document(context);
					newFileDoc.setDirectory(context, URL);
					newFileDoc.setName(context, file.getName());
					Calendar cal = file.getTimestamp();
					newFileDoc.setLastModificationDate(context, cal.getTime());
					if (file.isDirectory()) {
					newFileDoc.setFileType(context, sftp.proxies.FileType.Folder);
					} else {
					newFileDoc.setFileType(context, sftp.proxies.FileType.File);
					double fileSize = (double) file.getSize();
					String newSize = SFTPConnection.getFormattedNumber(context, fileSize, 0,0);
					newFileDoc.setSize(context, newSize);
					}
					try {
						newFileDoc.commit(context);
					} catch (CoreException e) {
						_logNode.error("File commit failed!",e);
						closeConnection(client);
						e.printStackTrace();
					}
					SFTPFileDocumentList.add(newFileDoc.getMendixObject());
					
				}
				
					
			} else if(sftpAction == Action.GET) {
				InputStream is = client.retrieveFileStream(URL + fileName);
				Core.storeFileDocumentContent(context, mendixDoc.getMendixObject(), is);
				is.close();
				if (!keepRemoteFile)
					client.deleteFile(URL + fileName); // remove file from destination
			} else if(sftpAction == Action.MKDIR) {
				String currentDir = connection.getCurrentDirectory(context);
				String newDir = currentDir+connection.getNewDocumentName(context);
				boolean directoryExists = client.changeWorkingDirectory(newDir+"/");
				if (directoryExists) {
					_logNode.warn(user + " tried to create " + newDir + "/ but it already exists. Directory not created");
					com.mendix.webui.FeedbackHelper.addTextMessageFeedback(context, IFeedback.MessageType.WARNING, "Folder " + newDir + "  does already exist, and was not created", true);
					SFTPConnection.resetNewFolderName(connection, context);
				} else {
					client.makeDirectory(newDir);
					_logNode.info("Folder " + newDir + " created by user " + user);
					SFTPConnection.resetNewFolderName(connection, context);
				}
			} else if(sftpAction == Action.PUT) {
				InputStream is = Core.getFileDocumentContent(context, mendixDoc.getMendixObject());
					client.storeFile(URL + fileName, is);
					is.close();
			} else if( sftpAction == Action.CD) {
				String parentFolder = SFTPConnection.getParentDirPath(connection.getCurrentDirectory(context)) +"/";
				connection.setCurrentDirectory(context, parentFolder);
			} else if(sftpAction == Action.ZIP) {
				File tempFile = File.createTempFile("zipfile", "tmp");
			    ZipOutputStream zipfile = new ZipOutputStream(new FileOutputStream(tempFile));
			    List<FileDocument> filesToZip = new ArrayList<FileDocument>();
			    List<IMendixObject> directoryList = Core.retrieveByPath(context, connection.getMendixObject(), "SFTP.DownloadDirectory_Connection");
			    
			    for (int i=0; i<directoryList.size(); i++) {
					IMendixObject directory = directoryList.get(i);
					String URLName = directory.getValue(context, DownloadDirectory.MemberNames.URL.toString());
					OutputStream out;
					File file = File.createTempFile("doc","tmp");
					out = new FileOutputStream(file);
					client.retrieveFile(URLName, out);
					InputStream is = new FileInputStream(file);

					FileDocument downloadedDoc = new FileDocument(context);
					String directoryFileName = directory.getValue(context, DownloadDirectory.MemberNames.FileName.toString());
					downloadedDoc.setName(directoryFileName);
					Core.storeFileDocumentContent(context, downloadedDoc.getMendixObject(), is);
					filesToZip.add(downloadedDoc);
					file.delete();
			    }

			    for (FileDocument file : filesToZip) {
			        InputStream fileInputStream = Core.getFileDocumentContent(context, file.getMendixObject());
			        zipfile.putNextEntry(new ZipEntry(file.getName()));
			        IOUtils.copy(fileInputStream, zipfile);
			        fileInputStream.close();
			    }
			    zipfile.close();
			    InputStream zipInputStream = new FileInputStream(tempFile);
			    Core.storeFileDocumentContent(context, mendixDoc.getMendixObject(), zipInputStream);
			    tempFile.delete();
			} else if(sftpAction == Action.RNM) {
				String oldName = URL + connection.getOldDocumentName(context);
				String newName =  URL + connection.getNewDocumentName(context);
				client.rename(oldName, newName);
			} else if(sftpAction == Action.RM) {
				String fileToDelete = URL + connection.getOldDocumentName(context);
				client.deleteFile(fileToDelete);
			} else {
				closeConnection(client);
				_logNode.error("Invalid action supplied");
			}
			closeConnection(client);
		} catch(IOException e) {
			_logNode.error("Exception occured while executing action",e);
			closeConnection(client);
		}
		return "";
		
	}
	
	private static void closeConnection(FTPClient client){
		try {
			client.logout();
			client.disconnect();
		} catch (IOException e) {
			_logNode.error("Error disconnecting from client",e);
			e.printStackTrace();
		}
	}



	
}

