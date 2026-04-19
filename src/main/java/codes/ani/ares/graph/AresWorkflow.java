package codes.ani.ares.graph;

import codes.ani.ares.model.AresState;
import codes.ani.ares.service.NotionIngestionService;
import codes.ani.ares.service.SanitizationService;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Defines the Ares state workflow that ingests requirements from Notion and sanitizes them.
 */
@Component
@ConditionalOnProperty(name = "ares.notion.mcp.enabled", havingValue = "true")
public class AresWorkflow {
    private final NotionIngestionService notionIngestionService;
    private final SanitizationService sanitizationService;

    /**
     * Creates the workflow with the required node services.
     *
     * @param notionIngestionService service used to ingest requirements from a Notion page
     * @param sanitizationService service used to sanitize ingested requirements
     */
    public AresWorkflow(NotionIngestionService notionIngestionService, SanitizationService sanitizationService) {
        this.notionIngestionService = notionIngestionService;
        this.sanitizationService = sanitizationService;
    }

    /**
     * Builds the state graph for the Ares pipeline.
     *
     * <p>The graph flow is: START -> ingestion -> sanitization -> END.
     * The ingestion node requires {@code notionPageId} in state metadata.
     *
     * @return configured {@link StateGraph} for {@link AresState}
     * @throws IllegalStateException if graph construction fails
     */
    public StateGraph<AresState> buildGraph() {
        try {
            return new StateGraph<>(AresState::new)
                    .addNode("ingestion", AsyncNodeAction.node_async(state -> {
                        String pageId = String.valueOf(state.getMetadata().getOrDefault("notionPageId", "")).trim();

                        if (pageId.isEmpty()) {
                            throw new Exception("Missing required metadata 'notionPageId' for ingestion node");
                        }

                        AresState nextState = notionIngestionService.ingestFromPage(state, pageId);

                        return Map.of("rawRequirements", nextState.getRawRequirements(), "auditTrail", nextState.getAuditTrail());
                    })).addNode("sanitization", AsyncNodeAction.node_async(state -> {
                        String sanitized = sanitizationService.sanitize(state.getRawRequirements());

                        var trail = new ArrayList<>(state.getAuditTrail());
                        trail.add("Sanitization completed");

                        return Map.of("sanitizedRequirement", sanitized, "auditTrail", Collections.unmodifiableList(trail));
                    })).addEdge(GraphDefinition.START, "ingestion").addEdge("ingestion", "sanitization").addEdge("sanitization", GraphDefinition.END);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Ares workflow graph", e);
        }
    }
}
