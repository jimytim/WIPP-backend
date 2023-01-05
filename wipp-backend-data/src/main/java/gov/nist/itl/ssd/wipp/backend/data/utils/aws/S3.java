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

package gov.nist.itl.ssd.wipp.backend.data.utils.aws;

import com.google.api.services.drive.model.File;
import gov.nist.itl.ssd.wipp.backend.data.utils.gdrive.GDriveException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Client;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;


public class S3 {

    private static final Logger LOGGER = Logger.getLogger(S3.class.getName());

    private static final Region region = Region.US_EAST_1;

    private final String UserID;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String bucketName;
    private final CustomCredentialsProvider credentials;

    public final S3Client s3;

    public S3(String userId, String bucketName, String accessKeyId, String secretAccessKey) {

        this.UserID = userId;
        this.bucketName = bucketName;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.credentials = new CustomCredentialsProvider(this.accessKeyId, this.secretAccessKey);
        this.s3 = S3Client.builder().credentialsProvider(credentials).region(region).build();
    }

    private String removePrefix(String s, String prefix)
    {
        if (s != null && prefix != null && s.startsWith(prefix)) {
            return s.substring(prefix.length());
        }
        return s;
    }

    public void downloadFile(String keyName, Path destination) {
        System.out.format("Downloading %s from S3 bucket %s...\n", keyName, this.bucketName);
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(this.bucketName).key(keyName).build();
            GetObjectResponse getObjectResponse = this.s3.getObject(request, destination);
        } catch (AwsServiceException e) {
            LOGGER.log(Level.SEVERE, "AWS Service Error " + e.getMessage());
        }
    }

    public final List<String> listFolder(String folderPath, String filters, boolean recursive) throws S3CustomException {
        LOGGER.log(Level.INFO, "Listing folder contents for: '" + folderPath + "'");
        int folderPathLength = folderPath.length();
        List<String> keyResults = new ArrayList<>();
        ArrayList<S3Object> fileObjects = new ArrayList<>();

        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(this.bucketName)
                .prefix(folderPath)
                .build();
        ListObjectsV2Response response = this.s3.listObjectsV2(req);
        List<S3Object> objects = response.contents();
        boolean extensionFiltering = false;
        List<String> extFilters = null;

        if (filters != null) {
            LOGGER.log(Level.INFO, "Using filtering for file extensions: " + filters);
            // Processing extension string list
            String[] filtersArray = filters.replaceAll("\\s", "").split(",");
            extFilters = new ArrayList<>();
            for (String filter : filtersArray) {
                extFilters.add(filter.substring(1, filter.length() - 1));
            }
            extensionFiltering = true;
        }

        if (!objects.isEmpty()) {
            LOGGER.log(Level.INFO, "Object list size = " + objects.size());
            if (objects.size() > 1) {
                for (S3Object obj : objects) {
                    String objKey = obj.key();
                    if (!objKey.endsWith("/")) {
                        LOGGER.log(Level.INFO, "Full key: " + objKey);
                        String filename = objKey.substring(folderPathLength);
                        LOGGER.log(Level.INFO, "Filename: " + filename);
                        if (filename.contains("/")) {
                            if (recursive) {
                                int subPathLength = filename.lastIndexOf('/');
                                filename = filename.substring(subPathLength);
                                LOGGER.log(Level.INFO, "New filename: " + filename);
                            } else {
                                LOGGER.log(Level.INFO, "Recursive mode off, skipping");
                                continue;
                            }
                        }
                        if (extensionFiltering) {
                            for (String ext : extFilters) {
                                if (filename.endsWith(ext)) {
                                    keyResults.add(objKey);
                                    break;
                                }
                            }
                        } else {
                            keyResults.add(objKey);
                        }
                    }
                }
                if (keyResults.isEmpty()) {
                    LOGGER.log(Level.SEVERE, "No files found with the requested file extensions");
                    throw new S3CustomException("No files found with the requested file extensions");
                }
            } else {
                LOGGER.log(Level.SEVERE, "The prefix does not match with any actual file. (Empty folder)");
                throw new S3CustomException("The prefix does not match with any actual file. (Empty folder)");
            }
        } else {
            LOGGER.log(Level.SEVERE, "Prefix/Folder not found. (Object list is empty)");
            throw new S3CustomException("Prefix/Folder not found.");
        }
        return keyResults;
    }

    public final void downloadFiles(List<String> fileKeys, String destinationFolder, String folderName) {
        for (String key : fileKeys) {
            Path destination = Paths.get(destinationFolder, removePrefix(key, folderName));
            downloadFile(key, destination);
        }

    }
}
