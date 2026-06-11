package codes.ani.ares.backend.dto;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuditMetadataUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String generateAuditMetadata(String githubToken, String notionToken) {
        try {
            Map<String, Object> audit = new HashMap<>();
            
            if (githubToken != null && !githubToken.trim().isEmpty()) {
                Map<String, Object> ghAudit = new HashMap<>();
                ghAudit.put("present", true);
                ghAudit.put("last4", getLast4(githubToken.trim()));
                ghAudit.put("hash", hashToken(githubToken.trim()));
                ghAudit.put("source", "GITHUB");
                ghAudit.put("timestamp", Instant.now().toString());
                audit.put("github", ghAudit);
            } else {
                audit.put("github", Map.of("present", false, "source", "GITHUB"));
            }

            if (notionToken != null && !notionToken.trim().isEmpty()) {
                Map<String, Object> notionAudit = new HashMap<>();
                notionAudit.put("present", true);
                notionAudit.put("last4", getLast4(notionToken.trim()));
                notionAudit.put("hash", hashToken(notionToken.trim()));
                notionAudit.put("source", "NOTION");
                notionAudit.put("timestamp", Instant.now().toString());
                audit.put("notion", notionAudit);
            } else {
                audit.put("notion", Map.of("present", false, "source", "NOTION"));
            }

            return objectMapper.writeValueAsString(audit);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String getLast4(String token) {
        if (token == null || token.length() <= 4) {
            return token;
        }
        return token.substring(token.length() - 4);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "error-hashing";
        }
    }
}
