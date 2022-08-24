package gov.nist.itl.ssd.wipp.backend.data.utils.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentials;

public class CustomAwsCredentialsResolver implements AwsCredentials {

    private final String accessKeyId;
    private final String secretAccessKey;

    CustomAwsCredentialsResolver(String accessKeyId, String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
        this.accessKeyId = accessKeyId;
    }

    @Override
    public String accessKeyId() {
        return accessKeyId;
    }

    @Override
    public String secretAccessKey() {
        return secretAccessKey;
    }
}