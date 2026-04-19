package codes.ani.ares.model;

import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;

@Getter
public class AresState extends AgentState {

    private final String requestId;
    private final String rawRequirements;
    private final String sanitizedRequirement;
    private final VerificationMode mode;
    private final List<String> auditTrail;
    private final Map<String, Object> metadata;

    public AresState(String requestId,
                     String rawRequirements,
                     String sanitizedRequirement,
                     VerificationMode mode,
                     List<String> auditTrail,
                     Map<String, Object> metadata) {
        super(Map.of("requestId", requestId));
        this.requestId = requestId;
        this.rawRequirements = rawRequirements;
        this.sanitizedRequirement = sanitizedRequirement;
        this.mode = mode;
        this.auditTrail = Collections.unmodifiableList(auditTrail);
        this.metadata = Collections.unmodifiableMap(metadata);
    }

    public AresState(Map<String, Object> data) {
        super(data == null ? Map.of() : data);

        Map<String, Object> source = data == null ? Map.of() : data;

        this.requestId = asString(source.get("requestId"));
        this.rawRequirements = asString(source.get("rawRequirements"));
        this.sanitizedRequirement = asString(source.get("sanitizedRequirement"));
        this.mode = asMode(source.get("mode"));
        this.auditTrail = toUnmodifiableStringList(source.get("auditTrail"));
        this.metadata = toUnmodifiableMap(source.get("metadata"));
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static VerificationMode asMode(Object value) {
        if (value == null) {
            return VerificationMode.UNKNOWN;
        }
        if (value instanceof VerificationMode mode) {
            return mode;
        }
        try {
            return VerificationMode.valueOf(String.valueOf(value).toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return VerificationMode.UNKNOWN;
        }
    }

    private static List<String> toUnmodifiableStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toUnmodifiableMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Collections.unmodifiableMap((Map<String, Object>) map);
    }


    public enum VerificationMode {WEB_UI, API, DATA, UNKNOWN}

    /**
     * Creates a default initial state for a request.
     *
     * @param requestId unique request identifier
     * @param metadata  optional metadata map to include in the state (e.g., Notion page ID)
     * @return initial {@link AresState} with empty requirement fields and unknown verification mode
     */
    public static AresState init(String requestId, Map<String, Object> metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("metadata", metadata);
        data.put("rawRequirements", "");
        data.put("sanitizedRequirement", "");
        data.put("auditTrail", new ArrayList<String>());
        return new AresState(data);
    }
}
