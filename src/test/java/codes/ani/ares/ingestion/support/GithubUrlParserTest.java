package codes.ani.ares.ingestion.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GithubUrlParserTest {

    private GithubUrlParser parser;

    @BeforeEach
    void setUp() {
        parser = new GithubUrlParser();
    }

    @Test
    void parseValidPrUrl_ReturnsPrRef() {
        String url = "https://github.com/ruddha2001/project-ares/pull/7";
        var ref = parser.parse(url);

        assertEquals("ruddha2001", ref.owner());
        assertEquals("project-ares", ref.repo());
        assertTrue(ref.isPullRequest());
        assertEquals(7, ref.prNumber());
    }

    @Test
    void parseValidRepoUrl_ReturnsRepoRef() {
        String url = "https://github.com/ruddha2001/minerva";
        var ref = parser.parse(url);

        assertEquals("ruddha2001", ref.owner());
        assertEquals("minerva", ref.repo());
        assertFalse(ref.isPullRequest());
        assertNull(ref.prNumber());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/ruddha2001/minerva/",
            "https://github.com/ruddha2001/project-ares/pull/7/"
    })
    void parseUrlsWithTrailingSlashes(String url) {
        assertDoesNotThrow(() -> parser.parse(url));
    }

    @Test
    void parseInvalidUrl_ThrowsException() {
        String url = "https://not-github.com/owner/repo";
        Exception exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(url));
        assertTrue(exception.getMessage().contains("Unsupported GitHub URI format"));
    }
}