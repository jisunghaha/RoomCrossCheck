package com.roomcrosscheck.advertisement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiExtractionProviderTest {
    @Test void disabledProviderDoesNotCallTheNetwork() {
        var provider = new GeminiExtractionProvider(new ObjectMapper(), "", "gemini-2.5-flash",
            "http://127.0.0.1:1", false);
        assertThrows(ResponseStatusException.class, () -> provider.extract("광고", null, null, null, null));
    }

    @Test void sendsImageAndKeyToGeminiEndpointAndParsesJson() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requestBody = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"rawText\\":\\"월세 42만원\\",\\"address\\":null,\\"depositWon\\":null,\\"monthlyRentWon\\":420000,\\"maintenanceFeeWon\\":null,\\"areaSqm\\":null,\\"floor\\":null}"}]}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new GeminiExtractionProvider(new ObjectMapper(), "test-key", "gemini-2.5-flash",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta", true);
            var result = provider.extract("", null, null, new byte[]{1, 2, 3}, "image/png");
            assertEquals("test-key", key.get());
            assertEquals(420_000L, result.monthlyRentWon());
            var request = new ObjectMapper().readTree(requestBody.get());
            assertEquals("AQID", request.path("contents").path(0).path("parts").path(1)
                .path("inline_data").path("data").asText());
        } finally { server.stop(0); }
    }
}
