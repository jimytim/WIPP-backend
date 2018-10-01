/**
 *
 */
package gov.nist.itl.ssd.wipp.backend.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Mylene Simon <mylene.simon at nist.gov>
 *
 */
@Component
public class CoreConfig {

    public static final String BASE_URI = "/api";

    @Value("${wipp.version}")
    private String wippVersion;
    
    @Value("${spring.data.mongodb.host}")
    private String mongodbHost;
    
    @Value("${spring.data.mongodb.database}")
    private String mongodbDatabase;
    
    @Value("${workflow.management.system:argo}")
    private String workflowManagementSystem;
    
    @Value("${storage.workflows}")
    private String workflowsFolder;

    @Value("${workflows.binary}")
    private String worflowsBinary;

    @Value("${storage.collections}")
    private String imagesCollectionsFolder;

    @Value("${storage.collections.upload.tmp}")
    private String collectionsUploadTmpFolder;

    @Value("${storage.temp.jobs}")
    private String jobsTempFolder;

    @Value("${ome.converter.threads:2}")
    private int omeConverterThreads;

    
	public String getWippVersion() {
		return wippVersion;
	}

	public String getMongodbHost() {
		return mongodbHost;
	}
	
	public String getMongodbDatabase() {
		return mongodbDatabase;
	}
	
	public String getWorkflowManagementSystem() {
		return workflowManagementSystem;
	}

	public String getWorkflowsFolder() {
		return workflowsFolder;
	}

	public String getWorflowsBinary() {
	    return worflowsBinary;
    }

	public String getImagesCollectionsFolder() {
        return imagesCollectionsFolder;
    }

	public String getCollectionsUploadTmpFolder() {
        return collectionsUploadTmpFolder;
    }

    public String getJobsTempFolder() {
        return jobsTempFolder;
    }

    public int getOmeConverterThreads() {
        return omeConverterThreads;
    }

}
