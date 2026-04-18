package codes.ani.ares.integration;

import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.sql.GcpCloudSqlAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration",
        "ares.notion.mcp.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "NOTION_TOKEN", matches = ".+")
class McpConnectionTest {

    @Autowired
    private McpClient notionMcpClient;

    @Test
    void shouldListNotionTools() {
        var tools = notionMcpClient.listTools();

        assertFalse(tools.isEmpty(), "MCP Client should discover tools from Notion");
        System.out.println("--- Discovered Notion MCP Tools ---");
        tools.forEach(tool -> System.out.println("Tool: " + tool.name()));
        System.out.println("------------------------------------");
    }
}
