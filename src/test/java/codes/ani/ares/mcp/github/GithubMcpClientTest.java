package codes.ani.ares.mcp.github;

import codes.ani.ares.mcp.config.McpProperties;
import codes.ani.ares.mcp.model.McpRequest;
import codes.ani.ares.mcp.model.McpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubMcpClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private McpProperties mcpProperties;

    private GithubMcpClientImpl client;

    @BeforeEach
    void setUp() {
        var config = new McpProperties.ProviderConfig();
        config.setServerUrl("http://localhost:3000");
        config.setAuthToken("test-token");
        when(mcpProperties.getProviders()).thenReturn(Map.of("github", config));

        client = new GithubMcpClientImpl(mcpProperties, restClient);
    }

    @ParameterizedTest
    @MethodSource("methodSuccessCases")
    void methods_WhenConfigured_SendsCorrectToolCall(String methodName, String toolName, String responseText) {
        var mockResponse = new McpResponse();
        var result = new McpResponse.McpResult();
        var content = new McpResponse.McpContent();
        content.setText(responseText);
        result.setContent(Collections.singletonList(content));
        mockResponse.setResult(result);

        var requestBodySpec = stubRestClientChainWithResponse(mockResponse);

        String payload = invokeConfiguredMethod(methodName);

        assertEquals(responseText, payload);

        ArgumentCaptor<McpRequest> requestCaptor = ArgumentCaptor.forClass(McpRequest.class);
        verify(requestBodySpec).body(requestCaptor.capture());
        assertEquals(toolName, requestCaptor.getValue().params().get("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"listRepositoryFiles", "pullRequestRead", "getFileContent"})
    void methods_WhenGithubConfigMissing_Throws(String methodName) {
        when(mcpProperties.getProviders()).thenReturn(Map.of());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokeMethodWithoutConfig(methodName));

        assertEquals("No MCP configuration found for: github", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"listRepositoryFiles", "pullRequestRead", "getFileContent"})
    void methods_WhenMcpResponseIsNull_Throws(String methodName) {
        stubRestClientChainWithResponse(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokeConfiguredMethod(methodName));

        assertEquals("Empty response from GitHub MCP server", getRootCause(ex).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"listRepositoryFiles", "pullRequestRead", "getFileContent"})
    void methods_WhenMcpResultIsNull_Throws(String methodName) {
        stubRestClientChainWithResponse(new McpResponse());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> invokeConfiguredMethod(methodName));

        assertEquals("Empty response from GitHub MCP server", getRootCause(ex).getMessage());
    }

    private String invokeConfiguredMethod(String methodName) {
        return switch (methodName) {
            case "listRepositoryFiles" -> client.listRepositoryFiles("owner", "repo", "", true).join();
            case "pullRequestRead" -> client.pullRequestRead("owner", "repo", 1L).join();
            case "getFileContent" -> client.getFileContent("owner", "repo", "README.md").join();
            default -> throw new IllegalArgumentException("Unsupported method: " + methodName);
        };
    }

    private void invokeMethodWithoutConfig(String methodName) {
        switch (methodName) {
            case "listRepositoryFiles" -> client.listRepositoryFiles("owner", "repo", "", true);
            case "pullRequestRead" -> client.pullRequestRead("owner", "repo", 1L);
            case "getFileContent" -> client.getFileContent("owner", "repo", "README.md");
            default -> throw new IllegalArgumentException("Unsupported method: " + methodName);
        }
    }

    private static Stream<Arguments> methodSuccessCases() {
        return Stream.of(
                Arguments.of("listRepositoryFiles", "list_repository_files", "[\"file1.js\", \"file2.js\"]"),
                Arguments.of("pullRequestRead", "pull_request_read", "{\"number\": 1}"),
                Arguments.of("getFileContent", "get_file_content", "console.log('hello');")
        );
    }

    private RestClient.RequestBodyUriSpec stubRestClientChainWithResponse(McpResponse response) {
        var requestBodySpec = mock(RestClient.RequestBodyUriSpec.class);
        var responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodySpec);
        when(requestBodySpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(McpRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(McpResponse.class)).thenReturn(response);

        return requestBodySpec;
    }

    private Throwable getRootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}