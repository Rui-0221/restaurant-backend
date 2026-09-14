package org.example.restaurant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.restaurant.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekHttpCancellationTest {
    @Test
    void readsStructuredOutputOverRealLocalHttp() throws Exception {
        var mapper = new ObjectMapper();
        String content = mapper.writeValueAsString(new DishSelectionResult(
                DishSelectionIntent.ASK_CLARIFICATION, List.of(), "请问几位？", DiningPreferences.empty()));
        byte[] body = mapper.writeValueAsBytes(Map.of("choices", List.of(Map.of(
                "finish_reason", "stop", "message", Map.of("content", content)))));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            assertEquals("Bearer test-only", exchange.getRequestHeaders().getFirst("Authorization"));
            var request = mapper.readTree(exchange.getRequestBody());
            assertFalse(request.path("stream").asBoolean());
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var result = adapter(server).select(request(), () -> false);
            assertEquals("请问几位？", result.reply());
            assertEquals(DishSelectionIntent.ASK_CLARIFICATION, result.intent());
        } finally { server.stop(0); }
    }

    @Test
    void cancellingAnInFlightHttpRequestStopsWaitingAndDiscardsItsResponse() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancelled = new AtomicBoolean(false);
        var executor = Executors.newSingleThreadExecutor();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            arrived.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var result = executor.submit(() -> adapter(server).select(request(), cancelled::get));
            assertTrue(arrived.await(3, TimeUnit.SECONDS));
            cancelled.set(true);
            var failure = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private DeepSeekDishSelectionAdapter adapter(HttpServer server) {
        var properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-only");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new DeepSeekDishSelectionAdapter(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    }

    private DishSelectionRequest request() {
        return new DishSelectionRequest("推荐", List.of(), List.of(), DiningPreferences.empty());
    }
}
