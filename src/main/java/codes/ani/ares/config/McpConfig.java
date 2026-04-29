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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class McpConfig {

    private final Environment environment;

    @Value("${ares.mcp.providers.notion.auth-token}")
    private String notionAuthToken;

    public McpConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @ConditionalOnProperty(name = "ares.mcp.providers.notion.enabled", havingValue = "true")
    public McpClient notionMcpClient() {
        if (notionAuthToken == null || notionAuthToken.isBlank()) {
            throw new IllegalStateException("Property 'ares.mcp.providers.notion.auth-token' must be set when MCP is enabled");
        }

        String serverCommand = environment.getProperty("ares.mcp.providers.notion.server-command", "npx");
        List<String> args = Binder.get(environment)
                .bind("ares.mcp.providers.notion.args", Bindable.listOf(String.class))
                .orElse(List.of("-y", "@notionhq/notion-mcp-server"));

        List<String> command = new ArrayList<>();
        command.add(serverCommand);
        command.addAll(args);

        System.out.println(notionAuthToken);

        var transport = new StdioMcpTransport.Builder()
                .command(command)
                .environment(Map.of("NOTION_TOKEN", notionAuthToken))
                .logEvents(true)
                .build();

        return new DefaultMcpClient.Builder()
                .initializationTimeout(Duration.ofMinutes(2))
                .transport(transport)
                .build();
    }
}
