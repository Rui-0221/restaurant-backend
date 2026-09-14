package org.example.restaurant.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeepSeekDishSelectionAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = mock(HttpClient.class);
    private AiProperties properties() {
        var p = new AiProperties(); p.setEnabled(true); p.setApiKey("test-only"); return p;
    }
    private DishSelectionRequest request() { return new DishSelectionRequest("推荐", List.of(), List.of(), DiningPreferences.empty()); }
    @SuppressWarnings("unchecked")
    private void reply(String content, String finish, int status) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        if (status == 200) when(response.body()).thenReturn(mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("finish_reason", finish, "message", Map.of("content", content))))));
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }
    @Test void sendsJsonRequestAndParsesPreferences() throws Exception {
        reply("{\"intent\":\"ASK_CLARIFICATION\",\"items\":[],\"reply\":\"几位用餐\",\"preferences\":{\"exclusions\":[\"花生\"],\"maxSpicyLevel\":0,\"partySize\":null,\"budget\":null,\"cuisines\":[]}}", "stop", 200);
        var result = new DeepSeekDishSelectionAdapter(client, mapper, properties()).select(request(), () -> false);
        assertEquals(List.of("花生"), result.preferences().exclusions());
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(capture.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("/chat/completions", capture.getValue().uri().getPath());
        assertEquals("Bearer test-only", capture.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals(15, capture.getValue().timeout().orElseThrow().toSeconds());
    }
    @ParameterizedTest @ValueSource(strings = {
        "", "not-json", "{\"items\":[]}{}", "{\"unexpected\":1}",
        "{\"items\":[{\"dishId\":1,\"amount\":1.5,\"reason\":\"a\"}]}",
        "{\"items\":[{\"dishId\":1,\"amount\":\"2\",\"reason\":\"a\"}]}",
        "{\"items\":[{\"dishId\":true,\"amount\":2,\"reason\":\"a\"}]}"
    })
    void rejectsMalformedOrCoercedJson(String content) throws Exception {
        reply(content, "stop", 200);
        assertThrows(AiProviderException.class, () -> new DeepSeekDishSelectionAdapter(client, mapper, properties()).select(request(), () -> false));
        assertEquals(2, mapper.readValue("\"2\"", Integer.class));
    }
    @Test void rejectsTruncatedAndHttpErrorResponses() throws Exception {
        reply("{}", "length", 200);
        assertThrows(AiProviderException.class, () -> new DeepSeekDishSelectionAdapter(client, mapper, properties()).select(request(), () -> false));
        reply("{}", "stop", 429);
        assertThrows(AiProviderException.class, () -> new DeepSeekDishSelectionAdapter(client, mapper, properties()).select(request(), () -> false));
    }
    @Test @SuppressWarnings("unchecked") void cancellationCancelsTheActualHttpFuture() {
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        var checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new DeepSeekDishSelectionAdapter(client, mapper, properties())
                .select(request(), () -> checks.incrementAndGet() > 2));
        assertTrue(pending.isCancelled());
    }
    @Test void alreadyCancelledOrDisabledRequestsNeverReachNetwork() {
        var adapter = new DeepSeekDishSelectionAdapter(client, mapper, properties());
        assertThrows(CancellationException.class, () -> adapter.select(request(), () -> true));
        var p = properties(); p.setApiKey("");
        assertThrows(AiProviderException.class, () -> new DeepSeekDishSelectionAdapter(client, mapper, p).select(request(), () -> false));
        verifyNoInteractions(client);
    }
}
