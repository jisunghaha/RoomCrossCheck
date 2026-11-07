package com.roomcrosscheck.extraction.client;

import tools.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class GeminiExtractionProviderTest {
    private static final String VALID = """
        {"rawText":"광고 원문", "address":null, "depositWon":null, "monthlyRentWon":null,
         "maintenanceFeeWon":null, "areaSqm":null, "floor":null}
        """;

    @Test void rejectsInvalidNumbersOptionsAndMalformedJsonAsBadGateway() throws Exception {
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
            for (String invalid : new String[]{
                    "{}", "[]", "null", "", "{invalid",
                    VALID.replace("\"depositWon\":null", "\"depositWon\":1.5"),
                    VALID.replace("\"monthlyRentWon\":null", "\"monthlyRentWon\":\"350000\""),
                    VALID.replace("\"floor\":null", "\"floor\":2.5"),
                    VALID.replace("\"address\":null", "\"address\":123"),
                    VALID.replace("\"rawText\":\"광고 원문\"", "\"rawText\":null"),
                    VALID.replace("\"areaSqm\":null", "\"areaSqm\":\"23\""),
                    VALID.replace("\"floor\":null", "\"floor\":null,\"options\":[123]"),
                    VALID.replace("\"floor\":null", "\"floor\":null,\"options\":\"냉장고\""),
                    VALID + " {}"}) {
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
            assertEquals("미상", result.areaType());
            var request = JsonMapper.builder().build().readTree(requestBody.get());
            assertEquals("AQID", request.path("contents").path(0).path("parts").path(1)
                .path("inline_data").path("data").asText());
        } finally { server.stop(0); }
    }

    @Test void parsesExplicitAreaTypesAndUsesUnknownForNull() throws Exception {
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
            for (String type : new String[]{"전용", "공급", "미상"}) {
                output.set(VALID.replace("\"areaSqm\":null", "\"areaSqm\":23,\"areaType\":\"" + type + "\""));
                assertEquals(type, provider.extract("면적 검증", null, null, null, null).areaType());
            }
            output.set(VALID.replace("\"areaSqm\":null", "\"areaSqm\":23,\"areaType\":null"));
            assertEquals("미상", provider.extract("면적 검증", null, null, null, null).areaType());
        } finally { server.stop(0); }
    }

    @Test void distinguishesEmptyRecognitionFromUnmentionedFieldsAndZero() throws Exception {
        var mapper = JsonMapper.builder().build();
        var client = mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked") var response = (java.net.http.HttpResponse<String>) mock(java.net.http.HttpResponse.class);
        when(client.send(any(java.net.http.HttpRequest.class),
            org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        var provider = new GeminiExtractionProvider(client, mapper, "local-test-key", "local-stub", "http://127.0.0.1", true);
        for (String output : new String[]{VALID, VALID.replace("\"maintenanceFeeWon\":null", "\"maintenanceFeeWon\":0"),
                VALID.replace("광고 원문", "   ")}) {
            var envelope = mapper.createObjectNode();
            envelope.putArray("candidates").addObject().putObject("content").putArray("parts").addObject().put("text", output);
            when(response.body()).thenReturn(mapper.writeValueAsString(envelope));
            if (output.contains("\"rawText\":\"   \"")) {
                assertEquals("NO_READABLE_TEXT", assertThrows(ExtractionFailure.class,
                    () -> provider.extract("원문", null, null, null, null)).code());
            } else {
                var result = provider.extract("원문", null, null, null, null);
                assertNull(result.depositWon());
                assertEquals(output.contains("\"maintenanceFeeWon\":0") ? 0L : null, result.maintenanceFeeWon());
            }
        }
    }

    @Test void classifiesHttpFailuresWithoutExposingProviderBody() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked") var response = (java.net.http.HttpResponse<String>) mock(java.net.http.HttpResponse.class);
        when(client.send(any(java.net.http.HttpRequest.class),
            org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.body()).thenReturn("private provider details and credentials");
        var provider = new GeminiExtractionProvider(client, JsonMapper.builder().build(), "local-test-key", "local-stub", "http://127.0.0.1", true);
        for (int status : new int[]{401, 403, 429, 408, 504, 500, 503}) {
            when(response.statusCode()).thenReturn(status);
            var error = assertThrows(ExtractionFailure.class, () -> provider.extract("원문", null, null, null, null));
            String expected = switch (status) {
                case 401, 403 -> "AUTHENTICATION_FAILED";
                case 429 -> "RATE_LIMITED";
                case 408, 504 -> "TIMEOUT";
                default -> "PROVIDER_UNAVAILABLE";
            };
            assertEquals(expected, error.code());
            assertFalse(error.getReason().contains("private"));
            assertFalse(error.getReason().contains("local-test-key"));
        }
    }

    @Test void distinguishesTransportTimeoutAndConnectionFailureWithoutWaiting() throws Exception {
        var client = mock(java.net.http.HttpClient.class);
        when(client.send(any(java.net.http.HttpRequest.class),
            org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new java.net.http.HttpTimeoutException("private host"))
            .thenThrow(new java.io.IOException("private host"));
        var provider = new GeminiExtractionProvider(client, JsonMapper.builder().build(), "local-test-key", "local-stub", "http://127.0.0.1", true);
        assertEquals("TIMEOUT", assertThrows(ExtractionFailure.class,
            () -> provider.extract("광고", null, null, null, null)).code());
        assertEquals("CONNECTION_FAILED", assertThrows(ExtractionFailure.class,
            () -> provider.extract("광고", null, null, null, null)).code());
    }
}
