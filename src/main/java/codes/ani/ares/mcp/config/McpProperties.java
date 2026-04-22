package codes.ani.ares.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "ares.mcp")
public class McpProperties {
    private Map<String, ProviderConfig> providers;

    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String serverUrl;
        private String authToken;
    }
}
