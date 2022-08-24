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

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Client;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


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

    public void downloadFile(String bucketName, String keyName, Path destination) {
        System.out.format("Downloading %s from S3 bucket %s...\n", keyName, bucketName);
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(keyName).build();
            GetObjectResponse getObjectResponse = this.s3.getObject(request, destination);
        } catch (AwsServiceException e) {
            LOGGER.log(Level.SEVERE, "AWS Service Error " + e.getMessage());
        }
    }

    public final List<S3Object> listFolder(String folderPath) {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(this.bucketName)
                .prefix(folderPath)
                .build();
        ListObjectsV2Response res = this.s3.listObjectsV2(req);
        return res.contents();
    }

    public final void downloadFiles(List<String> fileKeys, String destinationFolder, String folderName) {
        for (String key : fileKeys) {
            Path destination = Paths.get(destinationFolder, removePrefix(key, folderName));
            downloadFile(this.bucketName, key, destination);
        }

    }
}
