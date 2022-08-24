/*
 * This software was developed at the National Institute of Standards and
 * Technology by employees of the Federal Government in the course of
 * their official duties. Pursuant to title 17 Section 105 of the United
 * States Code this software is not subject to copyright protection and is
 * in the public domain. This software is an experimental system. NIST assumes
 * no responsibility whatsoever for its use by other parties, and makes no
 * guarantees, expressed or implied, about its quality, reliability, or
 * any other characteristic. We would appreciate acknowledgement if the
 * software is used.
 */
package gov.nist.itl.ssd.wipp.backend.data.utils.gdrive;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.DriveScopes;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.io.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nist.itl.ssd.wipp.backend.data.imagescollection.ImagesCollectionEventHandler;
import org.apache.commons.io.IOUtils;

public class GDrive {

    private static final Logger LOGGER = Logger.getLogger(GDrive.class.getName());

    /** Application name. */
    private static final String APPLICATION_NAME = "Google Drive API Java Interface";
    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    /** Global instance of the scopes required by this interface. */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_READONLY);

    final NetHttpTransport HTTP_TRANSPORT;
    final String UserID;
    public final Drive service;
    private final Credential credential;
    /** Application credentials path */
    private final String credentials_file_path;
    /** Directory to store authorization tokens. */
    private final String tokens_directory_path;

    public GDrive(String userId, String code, String credentials_file_path, String tokens_directory_path) throws GeneralSecurityException, IOException {

        this.UserID = userId;
        this.credentials_file_path = credentials_file_path;
        this.tokens_directory_path = tokens_directory_path;
        this.HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        this.credential = getCredential(userId, code);
        this.service = getService();
        LOGGER.log(Level.INFO, "coucou 3");
    }
    private GoogleClientSecrets getClientSecret() throws IOException {
//        InputStream in = DriveQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        InputStream in = new FileInputStream(this.credentials_file_path);

        if (in == null) {
            LOGGER.log(Level.SEVERE, "Resource not found: " + this.credentials_file_path);
//            throw new FileNotFoundException("Resource not found: " + this.credentials_file_path);
        } else {
            LOGGER.log(Level.INFO, "Found credentials file.");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        return clientSecrets;
    }

    private void checkCredential(Credential credential) {
        try {
            // refresh the credential to see if the refresh token is still valid
            credential.refreshToken();
            LOGGER.log(Level.INFO, "Refreshed: expires in: " + credential.getExpiresInSeconds());
        } catch (TokenResponseException e) {
            LOGGER.log(Level.WARNING, "Refreshing issue: Token might be invalid\n" + e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Credential createNewCredentials(GoogleAuthorizationCodeFlow flow, String userID, String code) throws IOException {
        String redirectUrl = "urn:ietf:wg:oauth:2.0:oob";
//        String url = flow.newAuthorizationUrl().setRedirectUri(redirectUrl).build();
//        System.out.println("Please open the following URL in your browser then type the authorization code:");
//        System.out.println("  " + url);

        GoogleTokenResponse response = null;
        try {
            response = flow.newTokenRequest(code).setRedirectUri(redirectUrl).execute();
        } catch (TokenResponseException e) {
            LOGGER.log(Level.SEVERE, "Invalid token request.\n" + e);
        }
        return flow.createAndStoreCredential(response, userID);
    }

    private Credential getCredential(String userID, String code) throws IOException {
        LOGGER.log(Level.INFO, "Loading client secrets...");
        GoogleClientSecrets clientSecrets = getClientSecret();
        LOGGER.log(Level.INFO, "Creating GoogleAuthorizationCodeFlow...");
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(this.tokens_directory_path)))
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential(userID);
        if (credential != null) {
            LOGGER.log(Level.INFO, "Found stored credentials for user: " + userID);
            checkCredential(credential);
        } else {
            LOGGER.log(Level.INFO, "Creating new credential for user: " + userID);
            credential = createNewCredentials(flow, userID, code);
        }
        return credential;
    }

    private Drive getService(){
        Drive service = new Drive.Builder(this.HTTP_TRANSPORT, JSON_FACTORY, this.credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        return service;
    }

    private File findFileByName(String name) throws IOException {
        List<File> files = this.service.files().list()
                .setSpaces("Drive")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setQ("name='" + name + "'")
                .setFields("nextPageToken, files(id, name, mimeType)")
                .execute()
                .getFiles();
        if (files.size() > 1) {
            LOGGER.log(Level.WARNING, "multiple files with name " + name + " exists.");
            return null;
        } else if (files.size() < 1) {
            return null;
        } else {
            return files.get(0);
        }
    }
    public void downloadFileByName(String DriveFileName, java.io.File downloadDirectory, String OutputFileName) throws IOException {
        File file = this.findFileByName(DriveFileName);
        java.io.File filePath = new java.io.File(downloadDirectory, OutputFileName);
        if (file != null) {
            try {
                OutputStream outputStream = new ByteArrayOutputStream();
                this.service.files().get(file.getId()).executeMediaAndDownloadTo(outputStream);
                ByteArrayOutputStream DataStream = (ByteArrayOutputStream) outputStream;
                OutputStream FileStream = new FileOutputStream(filePath);
                DataStream.writeTo(FileStream);
            } catch (GoogleJsonResponseException e) {
                LOGGER.log(Level.SEVERE, "Unable to move file: " + e.getDetails());
                throw e;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.log(Level.WARNING, "File not found ('" + DriveFileName + "')");
        }
    }
    public void downloadFiles(List<File> files, java.io.File downloadDirectory) throws IOException {
        for (File file: files) {
            String fileId = file.getId();
            String fileName = file.getName();
            java.io.File filePath = new java.io.File(downloadDirectory, fileName);
            try {
                OutputStream outputStream = new ByteArrayOutputStream();
                this.service.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                ByteArrayOutputStream DataStream = (ByteArrayOutputStream) outputStream;
                OutputStream FileStream = new FileOutputStream(filePath);
                DataStream.writeTo(FileStream);
            } catch (GoogleJsonResponseException e) {
                LOGGER.log(Level.SEVERE, "Unable to move file: " + e.getDetails());
                throw e;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public final List<File> getSubFolders(String googleFolderIdParent) throws IOException {

        String pageToken = null;
        List<File> list = new ArrayList<File>();

        String query = null;
        if (googleFolderIdParent == null) {
            query = " mimeType = 'application/vnd.google-apps.folder' " //
                    + " and 'root' in parents";
        } else {
            query = " mimeType = 'application/vnd.google-apps.folder' " //
                    + " and '" + googleFolderIdParent + "' in parents";
        }

        do {
            FileList result = this.service.files().list().setQ(query).setSpaces("drive") //
                    .setFields("nextPageToken, files(id, name)")//
                    .setPageToken(pageToken).execute();
            for (File file : result.getFiles()) {
                list.add(file);
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return list;
    }

    public final List<File> getGoogleRootFolders() throws IOException {
        return getSubFolders(null);
    }

    public final File solveFolderPath(String FolderPath) throws IOException {
        String[] folderList = FolderPath.split("/");
        boolean root = true;
        File parentFolder = null;
        String folderQuery = null;
        for (String FolderName : folderList) {
            if (FolderName != null && FolderName.length() != 0) {
                if (root) {
                    folderQuery = String.format("mimeType='application/vnd.google-apps.folder' and name = '%s' and 'root' in parents", FolderName);
                    root = false;
                } else {
                    folderQuery = String.format("mimeType='application/vnd.google-apps.folder' and name = '%s' and '%s' in parents", FolderName, parentFolder.getId());
                }
                FileList result = this.service.files().list().setQ(folderQuery)
                        .setSpaces("Drive")
                        .setSupportsAllDrives(true)
                        .setIncludeItemsFromAllDrives(true)
                        .setFields("files(id, name)")
                        .execute();
                List<File> parentFolderResult = result.getFiles();
                if (parentFolderResult.size() == 0) {
                    LOGGER.log(Level.SEVERE, FolderName + " not found");
                    break;
                } else {
                    parentFolder = parentFolderResult.get(0);
                }
            }
        }
        return parentFolder;
    }

    public final List<File> listFolder(String FolderPath) throws IOException {
        File ParentFolder = this.solveFolderPath(FolderPath);
        String folderId = ParentFolder.getId();
        String fileQuery = String.format("'%s' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false", folderId);

        List<File> fileResult = new ArrayList<File>();
        Drive.Files.List file_request = this.service.files().list().setQ(fileQuery);
        do {
            try {
                FileList files = file_request.execute();
                fileResult.addAll(files.getFiles());
                file_request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "An error occurred: " + e);
                file_request.setPageToken(null);
            }
        } while (file_request.getPageToken() != null && file_request.getPageToken().length() > 0);

        if (fileResult == null || fileResult.isEmpty()) {
            LOGGER.log(Level.WARNING, "No files found.");
        } else {
            LOGGER.log(Level.INFO, "Found " + fileResult.size() + " files");
        }
        return fileResult;
    }

    public void listFiles(int pageSize) throws IOException {
        // Print the names and IDs for up to 10 files.
        FileList result = this.service.files().list()
                .setPageSize(pageSize)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            LOGGER.log(Level.WARNING, "No files found.");
        } else {
            System.out.println("Files:");
            for (File file : files) {
                LOGGER.log(Level.INFO, file.getName() + " (" + file.getId() + ")\n");
            }
        }
    }
}
