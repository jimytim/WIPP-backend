package gov.nist.itl.ssd.wipp.backend.data.utils.gdrive;

public class GDriveException extends Exception {
    public GDriveException() {

    }

    public GDriveException(String message) {
        super(message);
    }

    public GDriveException(Throwable cause) {
        super(cause);
    }

    public GDriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
