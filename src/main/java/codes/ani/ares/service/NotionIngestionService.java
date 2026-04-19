package codes.ani.ares.service;

import codes.ani.ares.model.AresState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Json.toJson;

/**
 * Ingests requirement content from Notion through the configured MCP client
 * and maps it into the immutable {@link AresState} workflow model.
 */
@Service
@ConditionalOnProperty(name = "ares.notion.mcp.enabled", havingValue = "true")
public class NotionIngestionService {
    private final McpClient notionMcpClient;

    /**
     * Creates the service with the MCP client used to call Notion tools.
     *
     * @param notionMcpClient configured MCP client for Notion API tool execution
     */
    public NotionIngestionService(McpClient notionMcpClient) {
        this.notionMcpClient = notionMcpClient;
    }

    /**
     * Extracts all non-blank {@code plain_text} values from the raw Notion JSON payload
     * and joins them as newline-separated text.
     *
     * @param json raw JSON text returned by the MCP tool
     * @return normalized plain text content, or an empty string when input is blank
     */
    private String extractPlainText(String json) {
        if (json == null || json.isBlank()) return "";

        StringBuilder sb = new StringBuilder();

        Pattern pattern = Pattern.compile("\"plain_text\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            String match = matcher.group(1);
            if (!match.isBlank()) {
                sb.append(match).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Fetches child blocks for a Notion page, extracts plain text content,
     * and returns a new {@link AresState} with updated requirement text and audit trail.
     *
     * @param currentState current immutable processing state
     * @param pageId       Notion page (block) identifier to ingest
     * @return a new state containing ingested requirement content
     */
    public AresState ingestFromPage(AresState currentState, String pageId) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("API-get-block-children")
                .arguments(toJson(Map.of("block_id", pageId)))
                .build();

        ToolExecutionResult result = notionMcpClient.executeTool(request);
        String rawJson = result.resultText();
        String cleanedContent = extractPlainText(rawJson);

        var updatedTrail = new ArrayList<>(currentState.getAuditTrail());
        updatedTrail.add("Ingested requirements from Notion Page: " + pageId);

        return new AresState(
                currentState.getRequestId(),
                cleanedContent,
                currentState.getSanitizedRequirement(),
                currentState.getMode(),
                Collections.unmodifiableList(updatedTrail),
                currentState.getMetadata()
        );
    }
}
