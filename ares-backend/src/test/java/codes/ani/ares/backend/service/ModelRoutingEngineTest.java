package codes.ani.ares.backend.service;

import codes.ani.ares.backend.adapter.ModelProviderAdapter;
import codes.ani.ares.backend.adapter.OllamaProviderAdapter;
import codes.ani.ares.backend.adapter.GeminiProviderAdapter;
import codes.ani.ares.backend.adapter.ClaudeProviderAdapter;
import codes.ani.ares.backend.dto.JobInitializationRequest;
import codes.ani.ares.backend.dto.ProviderRequest;
import codes.ani.ares.backend.dto.ProviderResponse;
import codes.ani.ares.backend.enums.PipelineStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ModelRoutingEngineTest {

    private ModelProviderAdapter mockOllamaAdapter;
    private ModelProviderAdapter mockGeminiAdapter;
    private ModelProviderAdapter mockClaudeAdapter;
    private ModelRoutingEngine routingEngine;

    @BeforeEach
    public void setUp() {
        mockOllamaAdapter = mock(ModelProviderAdapter.class);
        mockGeminiAdapter = mock(ModelProviderAdapter.class);
        mockClaudeAdapter = mock(ModelProviderAdapter.class);

        when(mockOllamaAdapter.supports("ollama")).thenReturn(true);
        when(mockGeminiAdapter.supports("gemini-flash-3.5")).thenReturn(true);
        when(mockGeminiAdapter.supports("gemini")).thenReturn(true);
        when(mockClaudeAdapter.supports("claude-opus-4.6")).thenReturn(true);
        when(mockClaudeAdapter.supports("claude")).thenReturn(true);

        routingEngine = new ModelRoutingEngine(List.of(mockOllamaAdapter, mockGeminiAdapter, mockClaudeAdapter));
    }

    @Test
    public void testSuccessfulRoutingToOllama() {
        JobInitializationRequest context = new JobInitializationRequest(
                UUID.randomUUID(),
                "git@github.com:foo/bar.git",
                "spec",
                "raw",
                "diff",
                Map.of(PipelineStage.PR_SYNTHESIS, "ollama")
        );
        ProviderRequest request = new ProviderRequest("test prompt", "llama3", null);
        ProviderResponse expectedResponse = new ProviderResponse("Ollama Plan");

        when(mockOllamaAdapter.dispatch(request)).thenReturn(expectedResponse);

        ProviderResponse actualResponse = routingEngine.executeStage(PipelineStage.PR_SYNTHESIS, context, request);
        assertEquals(expectedResponse, actualResponse);
        verify(mockOllamaAdapter).dispatch(request);
        verify(mockGeminiAdapter, never()).dispatch(any());
        verify(mockClaudeAdapter, never()).dispatch(any());
    }

    @Test
    public void testSuccessfulRoutingToGeminiCaseInsensitive() {
        JobInitializationRequest context = new JobInitializationRequest(
                UUID.randomUUID(),
                "git@github.com:foo/bar.git",
                "spec",
                "raw",
                "diff",
                Map.of(PipelineStage.COMPLIANCE_EVALUATION, "  Gemini-Flash-3.5  ")
        );
        ProviderRequest request = new ProviderRequest("compliance check", "gemini-1.5-flash", null);
        ProviderResponse expectedResponse = new ProviderResponse("Gemini Compliance Report");

        when(mockGeminiAdapter.supports("gemini-flash-3.5")).thenReturn(true);
        when(mockGeminiAdapter.dispatch(request)).thenReturn(expectedResponse);

        ProviderResponse actualResponse = routingEngine.executeStage(PipelineStage.COMPLIANCE_EVALUATION, context, request);
        assertEquals(expectedResponse, actualResponse);
        verify(mockGeminiAdapter).dispatch(request);
        verify(mockOllamaAdapter, never()).dispatch(any());
        verify(mockClaudeAdapter, never()).dispatch(any());
    }

    @Test
    public void testFallbackRoutingWhenStageMissing() {
        JobInitializationRequest context = new JobInitializationRequest(
                UUID.randomUUID(),
                "git@github.com:foo/bar.git",
                "spec",
                "raw",
                "diff",
                Map.of()
        );
        ProviderRequest request = new ProviderRequest("test prompt", null, null);
        ProviderResponse expectedResponse = new ProviderResponse("Ollama Fallback Plan");

        when(mockOllamaAdapter.dispatch(request)).thenReturn(expectedResponse);

        ProviderResponse actualResponse = routingEngine.executeStage(PipelineStage.PR_SYNTHESIS, context, request);
        assertEquals(expectedResponse, actualResponse);
        verify(mockOllamaAdapter).dispatch(request);
    }

    @Test
    public void testUnresolvableProviderThrowsException() {
        JobInitializationRequest context = new JobInitializationRequest(
                UUID.randomUUID(),
                "git@github.com:foo/bar.git",
                "spec",
                "raw",
                "diff",
                Map.of(PipelineStage.COMPLIANCE_EVALUATION, "unknown-provider")
        );
        ProviderRequest request = new ProviderRequest("test prompt", null, null);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            routingEngine.executeStage(PipelineStage.COMPLIANCE_EVALUATION, context, request);
        });

        assertTrue(exception.getMessage().contains("Designated model provider target unresolvable: unknown-provider"));
    }

    @Test
    public void testOllamaAdapterSupportsSignatures() {
        RestClient.Builder builder = RestClient.builder();
        OllamaProviderAdapter adapter = new OllamaProviderAdapter(builder, "http://localhost:11434");

        assertTrue(adapter.supports("ollama"));
        assertTrue(adapter.supports("OLLAMA"));
        assertTrue(adapter.supports("  ollama-model  "));
        assertFalse(adapter.supports("gemini"));
        assertFalse(adapter.supports(null));
    }

    @Test
    public void testGeminiAdapterSupportsSignatures() {
        RestClient.Builder builder = RestClient.builder();
        GeminiProviderAdapter adapter = new GeminiProviderAdapter(builder, "https://generativelanguage.googleapis.com", "gemini-1.5-flash", "");

        assertTrue(adapter.supports("gemini"));
        assertTrue(adapter.supports("Gemini-Flash-3.5"));
        assertTrue(adapter.supports("  gemini-pro  "));
        assertFalse(adapter.supports("ollama"));
        assertFalse(adapter.supports(null));
    }

    @Test
    public void testClaudeAdapterSupportsSignatures() {
        RestClient.Builder builder = RestClient.builder();
        ClaudeProviderAdapter adapter = new ClaudeProviderAdapter(builder, "https://api.anthropic.com", "claude-3-5-sonnet-latest", "");

        assertTrue(adapter.supports("claude"));
        assertTrue(adapter.supports("Claude-Opus-4.6"));
        assertTrue(adapter.supports("  claude-sonnet  "));
        assertFalse(adapter.supports("gemini"));
        assertFalse(adapter.supports(null));
    }
}
