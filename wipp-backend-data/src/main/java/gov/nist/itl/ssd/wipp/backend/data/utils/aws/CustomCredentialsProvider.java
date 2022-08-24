package gov.nist.itl.ssd.wipp.backend.data.utils.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class CustomCredentialsProvider implements AwsCredentialsProvider {

    private final String accessKeyId;
    private final String secretAccessKey;

    public CustomCredentialsProvider(String accessKeyId, String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
        this.accessKeyId = accessKeyId;
    }

    @Override
    public AwsCredentials resolveCredentials() {
        return new CustomAwsCredentialsResolver(accessKeyId, secretAccessKey);
    }

}
