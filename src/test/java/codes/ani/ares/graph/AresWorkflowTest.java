package codes.ani.ares.graph;

import codes.ani.ares.model.AresState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "ares.notion.mcp.enabled=true",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration," +
                "com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration"
})
@EnabledIfEnvironmentVariable(named = "NOTION_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "NOTION_TEST_PAGE_ID", matches = ".+")
class AresWorkflowTest {

    @Autowired
    private AresWorkflow aresWorkflow;

    @Test
    void shouldRunFullWorkflowFromNotionToSanitization() throws Exception {
        String testPageId = System.getenv("NOTION_TEST_PAGE_ID");
        assertNotNull(testPageId, "Set NOTION_TEST_PAGE_ID environment variable");

        AresState startState = AresState.init("TEST-FLOW-001", Map.of("notionPageId", testPageId));

        var graph = aresWorkflow.buildGraph().compile();

        AresState finalState = graph.invoke(startState.data())
                .orElseThrow(() -> new IllegalStateException("Graph failed to return a final state"));

        assertNotNull(finalState, "Final state should not be null");

        assertFalse(finalState.getRawRequirements().isEmpty(), "Should have fetched requirements");

        assertNotNull(finalState.getSanitizedRequirement());

        List<String> logs = finalState.getAuditTrail();
        System.out.println("--- AUDIT TRAIL ---");
        logs.forEach(log -> System.out.println("- " + log));

        assertTrue(logs.size() >= 2, "Audit trail should have at least 2 entries");

        System.out.println("--- FINAL SANITIZED REQUIREMENT ---");
        System.out.println(finalState.getSanitizedRequirement());
    }
}