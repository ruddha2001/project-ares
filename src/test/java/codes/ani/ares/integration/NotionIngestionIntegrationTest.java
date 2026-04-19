package codes.ani.ares.integration;

import codes.ani.ares.model.AresState;
import codes.ani.ares.service.NotionIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
@EnabledIfEnvironmentVariable(named = "NOTION_TEST_PAGE_ID", matches = ".+")
class NotionIngestionIntegrationTest {
    @Autowired
    private NotionIngestionService notionIngestionService;

    @Test
    void shouldFetchRealDataFromNotion() {
        AresState initialState = AresState.init("NOTION-TEST-1", Map.of());

        String testPageId = System.getenv("NOTION_TEST_PAGE_ID");

        assertNotNull(testPageId, "You must set NOTION_TEST_PAGE_ID env var to run this test");

        AresState resultState = notionIngestionService.ingestFromPage(initialState, testPageId);

        assertNotNull(resultState.getRawRequirements(), "Raw requirement should not be null");
        assertFalse(resultState.getRawRequirements().isBlank(), "Raw requirement should have text content");

        assertTrue(resultState.getAuditTrail().stream()
                        .anyMatch(log -> log.contains("Ingested requirements from Notion Page")),
                "Audit trail should record the ingestion");

        System.out.println("--- INGESTED CONTENT ---");
        System.out.println(resultState.getRawRequirements());
        System.out.println("------------------------");
    }
}
