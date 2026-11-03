package com.roomcrosscheck.extraction.client;

import tools.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiExtractionProviderTest {
    @Test void rejectsFractionalMoneyFloorAndMalformedJsonAsBadGateway() throws Exception {
        var mapper = JsonMapper.builder().build();
        var output = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/local-stub:generateContent", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var root = mapper.createObjectNode();
            root.putArray("candidates").addObject().putObject("content")
                .putArray("parts").addObject().put("text", output.get());
            byte[] bytes = mapper.writeValueAsBytes(root);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var provider = new GeminiExtractionProvider(mapper, "local-test-key", "local-stub",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta", true);
            for (String invalid : new String[]{"{\"depositWon\":1.5}", "{\"floor\":2.5}", "{invalid"}) {
                output.set(invalid);
                var error = assertThrows(ResponseStatusException.class,
                    () -> provider.extract("광고", null, null, null, null));
                assertEquals(502, error.getStatusCode().value());
            }
        } finally { server.stop(0); }
    }

    @Test void disabledProviderDoesNotCallTheNetwork() {
        var provider = new GeminiExtractionProvider(JsonMapper.builder().build(), "", "gemini-2.5-flash",
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
            var provider = new GeminiExtractionProvider(JsonMapper.builder().build(), "test-key", "gemini-2.5-flash",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta", true);
            var result = provider.extract("", null, null, new byte[]{1, 2, 3}, "image/png");
            assertEquals("test-key", key.get());
            assertEquals(420_000L, result.monthlyRentWon());
            var request = JsonMapper.builder().build().readTree(requestBody.get());
            assertEquals("AQID", request.path("contents").path(0).path("parts").path(1)
                .path("inline_data").path("data").asText());
        } finally { server.stop(0); }
    }
}
