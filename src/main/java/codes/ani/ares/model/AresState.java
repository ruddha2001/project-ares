package codes.ani.ares.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable state container for a single Ares request lifecycle.
 *
 * @param requestId unique request identifier
 * @param rawRequirements original, unsanitized requirement text
 * @param sanitizedRequirement normalized/sanitized requirement text used downstream
 * @param mode selected verification mode for processing
 * @param auditTrail chronological log of processing steps
 * @param metadata additional request-scoped attributes
 */
public record AresState(
        String requestId,
        String rawRequirements,
        String sanitizedRequirement,
        VerificationMode mode,
        List<String> auditTrail,
        Map<String, Object> metadata
) {
    public enum VerificationMode {WEB_UI, API, DATA, UNKNOWN}

    /**
     * Creates a default initial state for a request.
     *
     * @param id unique request identifier
     * @return initial {@link AresState} with empty requirement fields and unknown verification mode
     */
    public static AresState init(String id) {
        return new AresState(id, "", "", VerificationMode.UNKNOWN, List.of(), Map.of());
    }
}
