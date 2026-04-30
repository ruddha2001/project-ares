package codes.ani.ares.exception;

/**
 * Thrown when no registered {@link codes.ani.ares.ingestion.IngestionProvider}
 * can handle a requested source URI.
 *
 * <p>Carries the error code {@code ARES_PROVIDER_NOT_FOUND} and is mapped to an
 * HTTP 400 Bad Request response by {@link codes.ani.ares.exception.handler.GlobalAresExceptionHandler}.</p>
 */
public class UnsupportedProviderException extends AresException {
    /**
     * Constructs an exception for the given unsupported URI.
     *
     * @param uri the source URI that could not be resolved to any provider
     */
    public UnsupportedProviderException(String uri) {
        super("No registered provider can handle the URI: " + uri, "ARES_PROVIDER_NOT_FOUND");
    }
}
