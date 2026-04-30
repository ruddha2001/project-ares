package codes.ani.ares.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Maps the {@code ares.mcp} configuration namespace to a type-safe Spring bean.
 *
 * <p>Supports a map of provider-specific configurations keyed by provider name
 * (e.g., {@code notion}, {@code github}). Each {@link ProviderConfig} holds the
 * connection and authentication settings for that provider's MCP server.</p>
 *
 * <p>Activated by Spring Boot's {@link ConfigurationProperties @ConfigurationProperties}
 * binding, making all properties under {@code ares.mcp.providers} available without
 * manual {@code @Value} annotations.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ares.mcp")
public class McpProperties {
    /**
     * Map of provider name to its specific MCP configuration.
     * Example: {@code providers.notion.enabled=true}
     */
    private Map<String, ProviderConfig> providers;

    /**
     * Configuration settings for a single MCP provider.
     */
    @Data
    public static class ProviderConfig {
        /**
         * Whether this MCP provider is enabled.
         */
        private boolean enabled;
        /**
         * Base URL for the MCP server (used by HTTP-based transports).
         */
        private String serverUrl;
        /**
         * Authentication token for this provider.
         */
        private String authToken;
        /**
         * Command to launch the MCP server process (used by STDIO transport).
         */
        private String serverCommand;
        /**
         * Additional command-line arguments for the server process.
         */
        private List<String> args;
    }
}
