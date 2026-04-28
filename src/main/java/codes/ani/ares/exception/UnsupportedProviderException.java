package codes.ani.ares.exception;

public class UnsupportedProviderException extends AresException {
    public UnsupportedProviderException(String uri) {
        super("No registered provider can handle the URI: " + uri, "ARES_PROVIDER_NOT_FOUND");
    }
}
