package gov.nist.itl.ssd.wipp.backend.data.utils.aws;

public class S3CustomException extends Exception {
    public S3CustomException() {

    }

    public S3CustomException(String message) {
        super(message);
    }

    public S3CustomException(Throwable cause) {
        super(cause);
    }

    public S3CustomException(String message, Throwable cause) {
        super(message, cause);
    }
}
