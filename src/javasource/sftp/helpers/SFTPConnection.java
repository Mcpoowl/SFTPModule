package sftp.helpers;

import sftp.proxies.Document;
import sftp.proxies.Configuration;
import sftp.proxies.Connection;
import sftp.proxies.DownloadDirectory;
import system.proxies.FileDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;








import org.apache.commons.io.IOUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.systemwideinterfaces.MendixRuntimeException;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IFeedback;
import com.mendix.systemwideinterfaces.core.IMendixObject;

public class SFTPConnection {
	
	public static String handleFile(FileDocument mendixDoc, Configuration configuration, Connection connection, List<IMendixObject> SFTPFileDocumentList, IContext context, Action sftpAction) {
		
		//*** configuration start
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
		String passPhrase = configuration.getPassPhrase(context);
		Integer port = configuration.getPort(context);
		Boolean keepRemoteFile = configuration.getKeepRemoteFile(context);
		File knownHostsFile = new File(new File(Core.getConfiguration().getResourcesPath().getPath(), "SFTP"), "known_hosts");
		File privateKeyFile = new File(new File(Core.getConfiguration().getResourcesPath().getPath(), "SFTP"), "private.key");
		
	    String fileName = "";
	    if (mendixDoc!=null) {
	    	fileName = mendixDoc.getName();
	    }
	    
	    String URL = connection.getCurrentDirectory(context);
	    
		try { 
			InputStream pki = new FileInputStream(privateKeyFile);
		    final byte[] prvkey = new byte[(int) privateKeyFile.length()]; // Private key must be byte array
		    // Read in the bytes
		    int offset = 0;
		    int numRead = 0;
		    while (offset < prvkey.length
		           && (numRead=pki.read(prvkey, offset, prvkey.length-offset)) >= 0) {
		        offset += numRead;
		    }
		    
		    byte[] emptyPassPhrase = new byte[0];
		    if (passPhrase!=null) {
		    	emptyPassPhrase = passPhrase.getBytes(); // password of key file	
		    }
		    		    
			JSch jsch = new JSch();
			jsch.setKnownHosts( new FileInputStream(knownHostsFile) );

			java.util.Properties config = new java.util.Properties(); 
			config.put("StrictHostKeyChecking", "no");
			
	        jsch.addIdentity(
	            user,    	     // String userName
	            prvkey,          // byte[] privateKey 
	            null,            // byte[] publicKey
	            emptyPassPhrase	 // byte[] passPhrase
	        );
	        
			Session session = jsch.getSession( user, host, port );    
			if (password != null && !password.isEmpty()) {
				session.setPassword( password ); // only do this if password is set
			}
			session.setConfig(config);
		//*** configuration end
			
			session.connect();
	
			Channel channel = session.openChannel( "sftp" );
			channel.connect();
						
			ChannelSftp sftpChannel = (ChannelSftp) channel;
			
			// input needs to define whether we are doing a get/retrieve
			if(sftpAction == Action.GET) {
				InputStream in = sftpChannel.get(URL + fileName);
				Core.storeFileDocumentContent(context, mendixDoc.getMendixObject(), in);
				in.close();
				if (!keepRemoteFile)
					sftpChannel.rm(URL + fileName); // remove file from destination
			} else if (sftpAction == Action.ZIP) {
				//Create RAR/ZIP here
				try {
					File tempFile = File.createTempFile("zipfile", "tmp");
				    ZipOutputStream zipfile = new ZipOutputStream(new FileOutputStream(tempFile));
				    List<FileDocument> filesToZip = new ArrayList<FileDocument>();
				    List<IMendixObject> directoryList = Core.retrieveByPath(context, connection.getMendixObject(), "SFTP.DownloadDirectory_Connection");
				    
				    for (int i=0; i<directoryList.size(); i++) {
						IMendixObject directory = directoryList.get(i);
						String URLName = directory.getValue(context, DownloadDirectory.MemberNames.URL.toString());
						InputStream in = sftpChannel.get(URLName);
						FileDocument downloadedDoc = new FileDocument(context);
						String directoryFileName = directory.getValue(context, DownloadDirectory.MemberNames.FileName.toString());
						downloadedDoc.setName(directoryFileName);
						Core.storeFileDocumentContent(context, downloadedDoc.getMendixObject(), in);
						filesToZip.add(downloadedDoc);
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
		
				} catch (FileNotFoundException e) {
					pki.close();
					e.printStackTrace();
				} catch (IOException e) {
					pki.close();
					e.printStackTrace();
				}
			
			
			} else if (sftpAction == Action.PUT) {
				//upload file
				InputStream out = Core.getFileDocumentContent(context, mendixDoc.getMendixObject());
				sftpChannel.put( out, URL + fileName);
			} else if (sftpAction == Action.LS) {
				// list directory
				Vector<?> fileList = sftpChannel.ls(URL);
				for (int i=0; i<fileList.size(); i++) {
					Object o = fileList.get(i);
					//make sure we have list entries
					if (o instanceof LsEntry) {
						LsEntry entry = (LsEntry) o;
						SftpATTRS attribs = entry.getAttrs();
						//only go for the files
						Document newFileDoc = new Document(context);
						newFileDoc.setDirectory(context, URL);
						newFileDoc.setName(context, entry.getFilename());
						SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd hh:mm:ss z yyyy",Locale.US);
						newFileDoc.setLastModificationDate(context, df.parse(attribs.getMtimeString(),new ParsePosition(0)));
						boolean isDirectory = attribs.isDir();
						if(isDirectory) {
							newFileDoc.setFileType(context, sftp.proxies.FileType.Folder);
						} else {
							newFileDoc.setFileType(context, sftp.proxies.FileType.File);
							double fileSize = (double) attribs.getSize();
							String newSize = getFormattedNumber(context, fileSize, 0,0);
							newFileDoc.setSize(context, newSize);
						}
						
						try {newFileDoc.commit(context);}
						catch (CoreException e) {
							pki.close();
							throw new MendixRuntimeException("File commit failed!",e);
						}
						SFTPFileDocumentList.add(newFileDoc.getMendixObject());
					}
				}
				//Do we create a new folder?
			} else if (sftpAction == Action.MKDIR) {
				String currentDir = connection.getCurrentDirectory(context);
				String newDir = currentDir+connection.getNewDocumentName(context);
				SftpATTRS attrs = null;
				try {
					//Try if the directory already exists
					attrs = sftpChannel.stat(newDir+"/");
				} catch (Exception e) {
					System.out.println(newDir + "/ not found. We should probably create it..");
				}
				//If the directory exists, do not create it..
				if (attrs != null) {
					Core.getLogger("(S)FTP").warn(user + " tried to create " + newDir + "/ but it already exists. Directory not created");
					com.mendix.webui.FeedbackHelper.addTextMessageFeedback(context, IFeedback.MessageType.WARNING, "Folder " + newDir + "  does already exist, and was not created", true);
					resetNewFolderName(connection, context);
					//Create the folder, remove the name from the Connection entity and commit it.
				} else {
					sftpChannel.mkdir(newDir);
					Core.getLogger("(S)FTP").info("Folder " + newDir + " created by user " + user);
					resetNewFolderName(connection, context);
				}
			} else if (sftpAction == Action.CD) {
				String parentFolder = getParentDirPath(connection.getCurrentDirectory(context)) +"/";
				connection.setCurrentDirectory(context, parentFolder);
			} else if (sftpAction == Action.RNM) {
				String oldName = URL + connection.getOldDocumentName(context);
				String newName =  URL + connection.getNewDocumentName(context);
				sftpChannel.rename(oldName, newName);
			} else {
				pki.close();
				throw new MendixRuntimeException("No correct SFTP action selected!");}
			
		
			sftpChannel.exit();
			session.disconnect();
			return "";
		} catch (JSchException e) {
			Core.getLogger("(S)FTP").error("JschException occured for document " + fileName + " with action " +
					sftpAction.toString() + ". Stacktrace: " + getStackTrace(e));
			throw new MendixRuntimeException("JschException occured for document " + fileName + " with action " +
										sftpAction.toString() + ".",e);

		} catch (SftpException e) {
			Core.getLogger("SFTP").error("SftpException occured for document " + fileName + " with action " +
					sftpAction.toString() + ". Stacktrace: " + getStackTrace(e));
			throw new MendixRuntimeException("SftpException occured for document " + fileName + " with action " +
					sftpAction.toString() + ".", e);
		} catch (IOException e) {
			Core.getLogger("SFTP").error("IOException occured for document " + fileName + " with action " +
					sftpAction.toString() + ". Stacktrace: " + getStackTrace(e));
			throw new MendixRuntimeException("IOException occured for document " + fileName + " with action " +
										sftpAction.toString() + ".",e);

		}
	}
	
	private static String getStackTrace(Throwable t)
	{
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		t.printStackTrace(pw);
		pw.flush();
		sw.flush();
		return sw.toString();
	}
	
	 static void resetNewFolderName(Connection connection, IContext context) {
		connection.setNewDocumentName(context, "");
		try { connection.commit(context); }
		catch (CoreException e) {
			throw new MendixRuntimeException("Connection commit failed!",e);	
		}
	}
	
	 static String getParentDirPath(String fileOrDirPath) {
	    boolean endsWithSlash = fileOrDirPath.endsWith("/");
	    return fileOrDirPath.substring(0, fileOrDirPath.lastIndexOf("/", 
	            endsWithSlash ? fileOrDirPath.length() - 2 : fileOrDirPath.length() - 1));
	}

	static String getFormattedNumber(IContext context, Double curValue, int minPrecision, int maxPrecision)
    {
        NumberFormat numberFormat = NumberFormat.getInstance(Core.getLocale(context));
        numberFormat.setMaximumFractionDigits(maxPrecision);
        numberFormat.setGroupingUsed(true);
        numberFormat.setMinimumFractionDigits(minPrecision);

        if (!Double.isNaN(curValue))
        {
            return numberFormat.format(curValue);
        }
    	return "";
    }
	
	
}
