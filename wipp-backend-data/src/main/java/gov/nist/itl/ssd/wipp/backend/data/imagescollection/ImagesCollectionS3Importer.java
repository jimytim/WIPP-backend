package gov.nist.itl.ssd.wipp.backend.data.imagescollection;

import gov.nist.itl.ssd.wipp.backend.data.utils.aws.S3;
import gov.nist.itl.ssd.wipp.backend.data.utils.aws.S3CustomException;
import gov.nist.itl.ssd.wipp.backend.core.CoreConfig;
import gov.nist.itl.ssd.wipp.backend.core.rest.exception.ClientException;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.Image;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageConversionService;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageHandler;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageRepository;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.metadatafiles.MetadataFileHandler;

import gov.nist.itl.ssd.wipp.backend.data.utils.gdrive.GDriveException;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

@Service
public class ImagesCollectionS3Importer {

    private static final Logger LOGGER = Logger.getLogger(ImagesCollectionS3Importer.class.getName());
    @Autowired
    CoreConfig config;

    @Autowired
    private ImagesCollectionRepository imagesCollectionRepository;

    @Autowired
    private ImageHandler imageHandler;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private MetadataFileHandler metadataHandler;

    @Autowired
    private ImageConversionService imageConversionService;

    protected void importFromS3Folder(ImagesCollection imagesCollection) {
        S3 userS3;

        List<String> fileKeys;
        boolean success = false;

        String imagesCollectionId = imagesCollection.getId();
        String folderPath = imagesCollection.getS3FolderName();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        try {

            String credential_path = config.getCloudAmazonFolder();
            LOGGER.log(Level.INFO, "Credentials: " + credential_path);
            LOGGER.log(Level.INFO, "Bucket: " + imagesCollection.getS3BucketName());
            LOGGER.log(Level.INFO, "Prefix: " + folderPath);
            LOGGER.log(Level.INFO, "Filtering: " + imagesCollection.getS3FileExtensions());
//            LOGGER.log(Level.INFO, "Access Key: " + imagesCollection.getS3AccessKeyID());
//            LOGGER.log(Level.INFO, "Secret Key: " + imagesCollection.getS3SecretAccessKey());

            userS3 = new S3(userId,
                            imagesCollection.getS3BucketName(),
                            imagesCollection.getS3AccessKeyID(),
                            imagesCollection.getS3SecretAccessKey());

            if (folderPath.equals("/")) {
                folderPath = "";
            }
            fileKeys = userS3.listFolder(folderPath, imagesCollection.getS3FileExtensions(), false);
            success = true;

        } catch (S3Exception s3e) {
            String s3message = s3e.getMessage();
            int endMessageStringIndex = s3message.indexOf('(');
            String s3messageTruncated = s3message.substring(0, endMessageStringIndex - 1);
            LOGGER.log(Level.SEVERE, "AWS S3 Error:\n" + s3message);
            throw new ClientException("AWS Error: " + s3messageTruncated);
        }catch (S3CustomException e) {
            LOGGER.log(Level.SEVERE, "S3 Exception:\n" + e.getMessage());
            throw new ClientException(e.getMessage());
        } finally {
            if (!success) {
                imagesCollectionRepository.delete(imagesCollection);
            }
        }
    //        userS3.downloadFiles(fileKeys, downloadFolder.getPath(), folderPath);
        try {
            File downloadFolder = imageHandler.getTempFilesFolder(imagesCollection.getId());
            String downloadFolderPath = downloadFolder.getPath();
            downloadFolder.mkdirs();

            LOGGER.log(Level.INFO, "DownloadFolder: " + downloadFolder);
            LOGGER.log(Level.INFO, "Downloading " + fileKeys.size() + " files.");
            for (String key : fileKeys) {
                LOGGER.log(Level.INFO, "File key: " + key);
                // TODO: Test -> keep full key as a filename but replace '/' delimiters with another character (like '-')
//                String fileName = key.replace('/', '-');
                int subPathLength = key.lastIndexOf('/');
                String fileName = key.substring(subPathLength + 1);
                LOGGER.log(Level.INFO, "fileName: " + key);
                Path destination = Paths.get(downloadFolderPath, fileName);
                userS3.downloadFile(key, destination);
                Image image = new Image(imagesCollectionId,
                        fileName,
                        fileName,
                        Files.size(destination),
                        true);
                imageRepository.save(image);
                imagesCollectionRepository.updateImagesCaches(imagesCollectionId);
                imageConversionService.submitImageToExtractor(image);
            }

            // Register images in collection
//            imageHandler.addAllInDbFromFolder(imagesCollection.getId(), downloadFolder.getPath());
//            imagesCollectionRepository.updateImagesCaches(imagesCollectionId);
//            List<Image> images = imageRepository.findByImagesCollection(imagesCollection.getId());

            // Start conversion
//            for (Image image : images) {
//                imageConversionService.submitImageToExtractor(image);
//            }

        } catch (S3Exception s3e) {
            String s3message = s3e.getMessage();
            int endMessageStringIndex = s3message.indexOf('(');
            String s3messageTruncated = s3message.substring(0, endMessageStringIndex - 1);
            LOGGER.log(Level.SEVERE, "AWS S3 Error:\n" + s3message);
            throw new ClientException("AWS Error: " + s3messageTruncated);
        } catch (IOException ex) {
            throw new ClientException("Error while importing data.");
        }
    }
}

