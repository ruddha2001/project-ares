package codes.ani.ares.config;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
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

    public McpConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @ConditionalOnProperty(name = "ares.mcp.providers.notion.enabled", havingValue = "true")
    public McpClient notionMcpClient() {
        String notionAuthToken = environment.getProperty("ares.mcp.providers.notion.auth-token");
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

    @Bean
    @ConditionalOnProperty(name = "ares.mcp.providers.github.enabled", havingValue = "true")
    public McpClient githubMcpClient() {
        String githubPatToken = environment.getProperty("ares.mcp.providers.github.auth-token");
        String githubMcpUrl = environment.getProperty("ares.mcp.providers.github.server-url", "https://api.githubcopilot.com/mcp/");
        if (githubPatToken == null || githubPatToken.isBlank()) {
            throw new IllegalStateException("Property 'ares.mcp.providers.github.auth-token' must be set when MCP is enabled");
        }

        var transport = new StreamableHttpMcpTransport.Builder()
                .url(githubMcpUrl)
                .customHeaders(Map.of("Authorization", "Bearer " + githubPatToken))
                .build();

        return new DefaultMcpClient.Builder()
                .initializationTimeout(Duration.ofMinutes(2))
                .transport(transport)
                .build();
    }
}
