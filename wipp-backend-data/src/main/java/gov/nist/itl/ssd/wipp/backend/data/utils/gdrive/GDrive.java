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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;

import java.io.*;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;
//import java.util.stream.Collectors;


public class GDrive {

    private static final Logger LOGGER = Logger.getLogger(GDrive.class.getName());

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Google Drive API Java Interface";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    /**
     * Global instance of the scopes required by this interface.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE_READONLY);

    /**
     * HTTP transport, which should normally be GoogleNetHttpTransport.newTrustedTransport() instead of
     * com.google.api.client.http.javanet.NetHttpTransport;
     */
    final NetHttpTransport httpTransport;
    final String UserID;
    public final Drive service;
    public final Credential credential;

    /**
     * Application credentials path
     */
    private final String credentials_file_path;

    /**
     * Directory to store authorization tokens.
     */
    private final String tokens_directory_path;

    /**
     * Class constructor.
     */
    public GDrive(String userId, String code, String credentials_file_path, String tokens_directory_path) throws GDriveException, IOException {
        this.UserID = userId;
        this.credentials_file_path = credentials_file_path;
        this.tokens_directory_path = tokens_directory_path;
        this.httpTransport = getTransport();
        this.credential = getCredential(userId, code);
        this.service = getService();
    }

    /**
     * Loads the WIPP client GoogleAPI credentials json file.
     */
    private GoogleClientSecrets getClientSecret() throws GDriveException, IOException {

//        InputStream in = DriveQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        try {
            Path path = Paths.get(this.credentials_file_path);
            LOGGER.log(Level.INFO, "Found credentials file.");

            InputStream in = Files.newInputStream(path);
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        } catch (InvalidPathException e) {
            LOGGER.log(Level.SEVERE, e.getReason());
            throw new GDriveException("WIPP API Credentials file not found.");
        }
    }

    /**
     * Validate OAuth 2.0 Credentials access token by trying to refresh them using the refresh token.
     *
     * @param credential the credentials to validate.
     */
    private void checkCredential(Credential credential) throws GDriveException {
        try {
            // refresh the credential to see if the refresh token is still valid
            credential.refreshToken();
            LOGGER.log(Level.INFO, "Refreshed: expires in " + credential.getExpiresInSeconds() + " seconds");
        } catch (TokenResponseException e) {
            LOGGER.log(Level.WARNING, "Refreshing issue: Token might be invalid\n" + e);
            throw new GDriveException("Refreshing issue: Token might be invalid.\n" + e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create new Google credentials using an authorization code.
     *
     * @param flow the Google flow used to request the credentials
     * @param userId the string used as a key in the Google DataStore to store the credential
     *               if a credential already exists for that key, it will be overwritten
     * @param code the code provided by the Google Authorization Server after the user logged in and granted WIPP
     *             with the Drive Scopes authorizations.
     *
     * @return the new credential instance set with a new pair of access token and refresh token
     */
    private Credential createNewCredentials(GoogleAuthorizationCodeFlow flow, String userId, String code) throws GDriveException {
        Credential credentials;

        try {
//            GoogleClientSecrets clientSecrets = getClientSecret();
//            GoogleClientSecrets.Details clientSecretsDetails = clientSecrets.getDetails();
//            String clientId = clientSecretsDetails.getClientId();
//            String clientSecret = clientSecretsDetails.getClientSecret();
//            String clientTokenUri = clientSecretsDetails.getTokenUri();
//            List<String> clientRedirectUris = clientSecretsDetails.getRedirectUris();
//            String clientFirstUri = clientRedirectUris.get(0);
//            tokenResponse = new GoogleAuthorizationCodeTokenRequest(new NetHttpTransport(), JSON_FACTORY,
//                                                                  clientTokenUri, clientId, clientSecret, code,
//                                                                  clientFirstUri).execute();

            String RedirectUri = "postmessage"; // Little undocumented magic here, as the actual uris do not work

            GoogleTokenResponse tokenResponse = flow.newTokenRequest(code).setRedirectUri(RedirectUri).execute();

//            LOGGER.log(Level.INFO, "Token response: " + tokenResponse);
//            String accessToken = tokenResponse.getAccessToken();
//            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
//            LOGGER.log(Level.INFO, "AccessToken: " + accessToken);

            credentials = flow.createAndStoreCredential(tokenResponse, userId);

        } catch (TokenResponseException e) {
            LOGGER.log(Level.SEVERE, "Invalid token request.\n" + e);
            throw new GDriveException("Invalid token request.\n" + e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
            throw new GDriveException("Error while creating new Google Credentials");
        }

        return credentials;
    }

    /**
     * Returns valid credentials for the user, necessary to the Drive service to call Google Drive's API
     * It either creates new one using an Authorization Code or tries to load stored credentials.
     *
     * @param userId the string used as a key in the Google DataStore to store the credential
     *               if a credential already exists for that key, it will be overwritten
     * @param code the code provided by the Google Authorization Server after the user logged in and granted WIPP
     *             with the Drive Scopes authorizations.
     *             If provided, new credentials are created and might overwrite previously stored one for the user
     *             If not provided, tries to load them from the DataStore or throw an exception
     *
     * @return the OAuth2 credential for the user's Google Drive account
     */
    private Credential getCredential(String userId, String code) throws GDriveException {
        Credential credential;
        try {
            LOGGER.log(Level.INFO, "Loading client secrets...");
            GoogleClientSecrets clientSecrets = getClientSecret();
            LOGGER.log(Level.INFO, "Creating GoogleAuthorizationCodeFlow...");

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    this.httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(this.tokens_directory_path)))
                    .setAccessType("offline")
                    .build();

            if (code != null) {
                LOGGER.log(Level.INFO, "Code provided. Creating new credential for user: " + userId);
                credential = createNewCredentials(flow, userId, code);
            } else {
                LOGGER.log(Level.INFO, "No code provided. Looking for old credentials...");
                credential = flow.loadCredential(userId);
                if (credential == null) {
                    throw new GDriveException("No Google credentials found.\nPlease sign in first.");
                } else {
                    LOGGER.log(Level.INFO, "Found stored credentials for user: " + userId);
                }
            }
            checkCredential(credential);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
            throw new GDriveException("Error while retrieving Google Credentials.");
        }
        return credential;
    }

    /**
     * Creates a Google HTTP secured transport (instead of using NetHttpTransport)
     */
    private NetHttpTransport getTransport() throws GDriveException {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new GDriveException("Error while creating the http transport.");
        }
    }

    /**
     * Creates a Google Drive Service with the instance attributes
     */
    private Drive getService() {
        return new Drive.Builder(this.httpTransport, JSON_FACTORY, this.credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void downloadFile(File file, java.io.File downloadDirectory) throws IOException {
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

    /**
     * Returns the Google Drive file representing the folder defined in the path provided
     * It iteratively finds each one of its potentials parents following the path until it reaches the leaf folder.
     * The full path must be explored to avoid the edge case with multiple folders using the same name in the file tree.
     *
     * @param folderPath String representing the path to the folder, '/' representing the root folder
     *                   (because the empty string not allowed)
     */
    public final File solveFolderPath(String folderPath) throws GDriveException {
        File folder = null;

        if (folderPath.equals("/")){
            folder = new File();
            folder.setId("root");
            folder.setName("root");
        } else {
            String[] folderList = folderPath.split("/");
            boolean root = true;

            String folderQuery;
            // We get the Ids of each parent folder iteratively from the root to the end folder
            for (String folderName : folderList) {
                if (folderName != null && folderName.length() != 0) {
                    if (root) {
                        folderQuery = String.format("mimeType='application/vnd.google-apps.folder' and name = '%s' and 'root' in parents", folderName);
                        root = false;
                    } else {
                        folderQuery = String.format("mimeType='application/vnd.google-apps.folder' and name = '%s' and '%s' in parents", folderName, folder.getId());
                    }
                    try {
                        FileList result = this.service.files().list().setQ(folderQuery)
                                .setSpaces("Drive")
                                .setSupportsAllDrives(true)
                                .setIncludeItemsFromAllDrives(true)
                                .setFields("files(id, name)")
                                .execute();

                        List<File> FolderResult = result.getFiles();
                        if (FolderResult.size() == 0) {
                            LOGGER.log(Level.SEVERE, "'" + folderName + "' folder not found");
                            throw new GDriveException("Folder '" + folderName + "' not found in the Google Drive.");
                        } else {
                            folder = FolderResult.get(0);
                        }

                    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                        LOGGER.log(Level.SEVERE, "Invalid request for the folder info:\n" + e);
                        throw new GDriveException("Error while parsing the folder path");
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, e.getMessage());
                        throw new GDriveException("Error while parsing the folder path");
                    }

                }
            }
        }
        return folder;
    }

    /**
     * Returns (only) the last extension of the file name (with the '.' character)
     *
     * @param filename String of the filename
     */
    public final Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".")));
    }

    /**
     * Builds and executes the Google Drive API V3 files.list() request using the provided query
     * Each API requests can only return 1000 results at maximum,
     * so the request is executed multiple times if necessary and appends the results
     *
     * @param query string of query
     *
     * @return      the Google Drive file list
     */
    private List<File> requestFileList(String query) {
        int pageSize = 1000; // [default: 100] [minimum: 1] [maximum: 1000]
        List<File> fileList = new ArrayList<>();
        try {
            Drive.Files.List fileRequest = this.service.files().list().setQ(query)
                    .setPageSize(pageSize)
                    .setSpaces("drive");
            // Deprecated since June 1, 2020
            // .setSupportsAllDrives(true)
            // .setIncludeItemsFromAllDrives(true)
            do {
                try {
                    FileList filesReqResult = fileRequest.execute();
                    fileList.addAll(filesReqResult.getFiles());
                    fileRequest.setPageToken(filesReqResult.getNextPageToken());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "An error occurred: " + e);
                    fileRequest.setPageToken(null);
                }
            } while (fileRequest.getPageToken() != null && fileRequest.getPageToken().length() > 0);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error while creating the file list request: " + e);
        }

        return fileList;
    }

    /**
     * Lists the contents of a Google Drive folder.
     * It requests all the files first, potentially recursively. This is done even if file extension filters are
     * provided due to the limitation of the Drive API, as it does not support all mimetypes.
     * The filtering is performed after.
     *
     * @param folderPath String of the folder's path
     * @param filters String of the files extensions if provided by the user (ex: "'.jpeg', '.png'")
     *                The filtering with the String.endsWith() method for each extensions.
     * @param recursive Flag for the recursive exploration of the folder with its subfolder
     *
     * @return      the Google Drive file list
     */
    public final List<File> listFolder(String folderPath, String filters, boolean recursive) throws IOException, GDriveException {
        LOGGER.log(Level.INFO, "Listing folder contents for: '" + folderPath + "'");
        List<File> results = new ArrayList<>();
        List<File> fileList = new ArrayList<>();
        File folder = this.solveFolderPath(folderPath);

        if (recursive) {
            LOGGER.log(Level.INFO, "Using recursive mode.");
            String fileQueryWithFolders = "'%s' in parents and trashed = false";

            Queue<File> folderQueue = new LinkedList<>();
            folderQueue.add(folder);

            while (!folderQueue.isEmpty()) {
                File currentFolder = folderQueue.poll();
                LOGGER.log(Level.INFO, "Listing folder: '" + currentFolder.getName() + "'");
                String query = String.format(fileQueryWithFolders, currentFolder.getId());
                List<File> currentFolderFiles = requestFileList(query);
                for (File file : currentFolderFiles) {
                    // If subfolder, we add it to the queue
                    if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
//                        LOGGER.log(Level.INFO, "Found a subfolder: '" + file.getName() + "'");
                        folderQueue.add(file);
                    } else {
//                        LOGGER.log(Level.INFO, "File: '" + file.getName() + "'");
                        fileList.add(file);
                    }
                }
            }

        } else {
            String fileQuery = String.format("'%s' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false", folder.getId());
            fileList = requestFileList(fileQuery);
        }

        if (!fileList.isEmpty()) {
            LOGGER.log(Level.INFO, "Found " + fileList.size() + " files");

            if (filters != null) {
                LOGGER.log(Level.INFO, "Using filtering for file extensions: " + filters);
                String[] filtersArray = filters.replaceAll("\\s", "").split(",");
                // Processing extension string list
                List<String> extFilters = new ArrayList<>();
                for (String filter : filtersArray) {
                    extFilters.add(filter.substring(1, filter.length() - 1));
                }

                //            Set<String> ExtFilterSet = ExtFilter.stream().collect(Collectors.toSet());
                for (File file : fileList) {
                    String filename = file.getName();
                    // Only filtering on the last file extension
//                    if (ExtFilterSet.contains(getExtension(filename).get())) {
//                        results.add(file);
//                    }
                    // Filtering by string's end (Slower check due to cartesian product)
                    for (String ext : extFilters) {
                        if (filename.endsWith(ext)) {
                            results.add(file);
                        }
                    }
                }
                if (results.isEmpty()) {
                    LOGGER.log(Level.SEVERE, "No files found with the requested file extensions");
                    throw new GDriveException("No files found with the requested file extensions");
                }
            } else {
                results = fileList;
            }

        } else {
            LOGGER.log(Level.SEVERE, "No files found in : '" + folderPath + "'");
            throw new GDriveException("'" + folderPath + "' is empty.");
        }
        return results;
    }
}

