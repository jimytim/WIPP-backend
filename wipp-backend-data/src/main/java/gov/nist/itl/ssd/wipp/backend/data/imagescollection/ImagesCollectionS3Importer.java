package gov.nist.itl.ssd.wipp.backend.data.imagescollection;

import gov.nist.itl.ssd.wipp.backend.data.utils.aws.S3;
import gov.nist.itl.ssd.wipp.backend.core.CoreConfig;
import gov.nist.itl.ssd.wipp.backend.core.rest.exception.ClientException;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.Image;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageConversionService;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageHandler;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageRepository;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.metadatafiles.MetadataFileHandler;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
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

        String credential_path = config.getCloudAmazonFolder();
        File downloadFolder = imageHandler.getTempFilesFolder(imagesCollection.getId());
        downloadFolder.mkdirs();

        LOGGER.log(Level.INFO, "Credentials: " + credential_path);
        LOGGER.log(Level.INFO, "DownloadFolder: " + downloadFolder);

        S3 userS3 = new S3(SecurityContextHolder.getContext().getAuthentication().getName(),
                           imagesCollection.getS3BucketName(),
                           imagesCollection.getS3AccessKeyID(),
                           imagesCollection.getS3SecretAccessKey());

        String folderPath = imagesCollection.getS3FolderName();
        List<S3Object> objects = userS3.listFolder(folderPath);

        List<String> fileKeys = objects.subList(1, objects.size()).stream().map(S3Object::key).collect(toList());
        userS3.downloadFiles(fileKeys, downloadFolder.getPath(), folderPath);

        // Register images in collection
        imageHandler.addAllInDbFromFolder(imagesCollection.getId(), downloadFolder.getPath());
        List<Image> images = imageRepository.findByImagesCollection(imagesCollection.getId());

        // Start conversion
        for (Image image : images) {
            imageConversionService.submitImageToExtractor(image);
        }

//        } catch (IOException ex) {
//            throw new ClientException("Error while importing data.");
//        }
    }
}

