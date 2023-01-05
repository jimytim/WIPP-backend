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
package gov.nist.itl.ssd.wipp.backend.data.imagescollection;

import gov.nist.itl.ssd.wipp.backend.data.utils.gdrive.GDrive;
import gov.nist.itl.ssd.wipp.backend.core.CoreConfig;
import gov.nist.itl.ssd.wipp.backend.core.rest.exception.ClientException;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.Image;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageConversionService;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageHandler;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.images.ImageRepository;
import gov.nist.itl.ssd.wipp.backend.data.imagescollection.metadatafiles.MetadataFileHandler;
import gov.nist.itl.ssd.wipp.backend.data.utils.gdrive.GDriveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for Google Drive import
 @author Timothee Kheyrkhah <timothee.kheyrkhah at nist.gov>
 **/
@Service
public class ImagesCollectionGDriveImporter {

    private static final Logger LOGGER = Logger.getLogger(ImagesCollectionGDriveImporter.class.getName());
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

    protected void importFromGDriveFolder(ImagesCollection imagesCollection) {
        GDrive userDrive;
        List<com.google.api.services.drive.model.File> files;
        boolean success = false;

        String imagesCollectionId = imagesCollection.getId();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        try {

            String credential_path = config.getCloudGoogleFolder() + "/credentialsDevWIPP.json";
            String token_dir_path = config.getCloudGoogleFolder() + "/tokens";
            LOGGER.log(Level.INFO, "Credentials path: " + credential_path);
            LOGGER.log(Level.INFO, "Token directory: " + token_dir_path);


            String code = imagesCollection.getGdriveCode();
            userDrive = new GDrive(userId, code, credential_path, token_dir_path);

            files = userDrive.listFolder(imagesCollection.getGdriveFolderName(),
                                         imagesCollection.getGdriveFileExtensions(),
                                         imagesCollection.getGdriveRecursive());

            success = true;
        } catch (GDriveException exception) {
            LOGGER.log(Level.SEVERE, "Google Drive Error:\n" + exception.getMessage());
            throw new ClientException(exception.getMessage());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Caught exception:\n" + ex);
            throw new ClientException("Error while importing data.\n" + ex.getMessage());
        } finally {
            if (!success) {
                imagesCollectionRepository.delete(imagesCollection);
            }
        }

        try {
//            LOGGER.log(Level.INFO, "files: " + files);

            File downloadFolder = imageHandler.getTempFilesFolder(imagesCollectionId);
            downloadFolder.mkdirs();

            for (com.google.api.services.drive.model.File file : files) {
                String fileName = file.getName();
//                java.lang.Long fileSize = file.getSize();

                Image image = new Image(imagesCollectionId,
                        fileName,
                        fileName,
                        0,
                        true);
                imageRepository.save(image);
                imagesCollectionRepository.updateImagesCaches(imagesCollectionId);
                imageConversionService.submitImageToExtractor(userDrive, image, file);
            }

//            for (com.google.api.services.drive.model.File file : files) {
//            userDrive.downloadFile(file, downloadFolder);
//            }

            // Register images in collection
//            imageHandler.addAllInDbFromFolder(imagesCollection.getId(), downloadFolder.getPath());
//            List<Image> images = imageRepository.findByImagesCollection(imagesCollection.getId());

            // Copy images to collection temp folder and start conversion
//            for(Image image : images) {
//                imageConversionService.submitImageToExtractor(image);
//            }

        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Error:\n" + exception.getMessage());
            throw new ClientException(exception.getMessage());
        }
    }
}
