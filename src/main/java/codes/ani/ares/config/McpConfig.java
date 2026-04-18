package codes.ani.ares.config;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring configuration for constructing MCP clients used by the application.
 */
@Configuration
public class McpConfig {

    private final Environment environment;

    /**
     * Creates the configuration with access to Spring environment properties.
     *
     * @param environment Spring environment used to resolve MCP configuration values
     */
    public McpConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Notion MCP token injected from configuration.
     */
    @Value("${ares.notion.mcp.token:}")
    private String notionToken;

    /**
     * Builds a Notion MCP client when MCP integration is enabled.
     *
     * @return configured MCP client using stdio transport
     * @throws IllegalStateException if the Notion token is missing while MCP is enabled
     */
    @Bean
    @ConditionalOnProperty(name = "ares.notion.mcp.enabled", havingValue = "true")
    public McpClient notionMcpClient() {
        if (notionToken == null || notionToken.isBlank()) {
            throw new IllegalStateException("Property 'ares.notion.mcp.token' must be set when MCP is enabled");
        }

        String serverCommand = environment.getProperty("ares.notion.mcp.server-command", "npx");
        List<String> args = Binder.get(environment)
                .bind("ares.notion.mcp.args", Bindable.listOf(String.class))
                .orElse(List.of("-y", "@notionhq/notion-mcp-server"));

        List<String> command = new ArrayList<>();
        command.add(serverCommand);
        command.addAll(args);

        var transport = new StdioMcpTransport.Builder().command(command).environment(Map.of("NOTION_TOKEN", notionToken)).logEvents(true).build();

        return new DefaultMcpClient.Builder().transport(transport).build();
    }
}
