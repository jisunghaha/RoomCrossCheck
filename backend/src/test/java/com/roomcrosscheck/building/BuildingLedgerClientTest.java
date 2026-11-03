package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BuildingLedgerClientTest {
    final LedgerQuery query=new LedgerQuery("1168010100","737","0","0",null,null);
    @Test void readsAllPagesAndRejectsApiErrorsWithoutLeakingKey() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var calls=new AtomicInteger();
        server.createContext("/getBrTitleInfo",e -> {
            int call=calls.incrementAndGet();
            assertTrue(e.getRequestURI().getRawQuery().contains("bun=0737"));
            String body=call<=2 ? "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":2,\"items\":{\"item\":{\"mgmBldrgstPk\":\""+call+"\"}}}}}"
                : "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"DO NOT EXPOSE SECRET\"}}}";
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();
        });
        server.start();
        try {
            var client=new BuildingLedgerClient(new ObjectMapper(),"private-test-key","http://127.0.0.1:"+server.getAddress().getPort());
            assertEquals(2,client.fetch("getBrTitleInfo",query,null,null).size());
            var error=assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null));
            assertFalse(error.getReason().contains("private-test-key")); assertFalse(error.getReason().contains("SECRET"));
            assertTrue(error.getReason().contains("30"));
        } finally { server.stop(0); }
    }
    @Test void missingCredentialDoesNotAttemptNetwork() {
        var client=new BuildingLedgerClient(new ObjectMapper(),"","http://127.0.0.1:1");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null)).getStatusCode().value());
    }
    @Test void acceptsXmlAndUnwrappedJsonAndSeparatesGatewayAuthenticationErrors() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var payload=new AtomicReference<String>();
        server.createContext("/getBrTitleInfo",e -> {
            byte[] bytes=payload.get().getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,bytes.length); e.getResponseBody().write(bytes); e.close();
        });
        server.start();
        try {
            var client=new BuildingLedgerClient(new ObjectMapper(),"test-key","http://127.0.0.1:"+server.getAddress().getPort());
            payload.set("<response><header><resultCode>00</resultCode></header><body><totalCount>1</totalCount><items><item><mgmBldrgstPk>123</mgmBldrgstPk></item></items></body></response>");
            assertEquals("123",client.fetch("getBrTitleInfo",query,null,null).get(0).path("mgmBldrgstPk").asText());
            payload.set("{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0,\"items\":{\"item\":[]}}}");
            assertTrue(client.fetch("getBrTitleInfo",query,null,null).isEmpty());
            payload.set("<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode><returnAuthMsg>SECRET</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>");
            var auth=assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null));
            assertTrue(auth.getReason().contains("코드 30")); assertFalse(auth.getReason().contains("SECRET"));
            payload.set("{\"response\":{\"header\":{},\"body\":{}}}");
            var missing=assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null));
            assertTrue(missing.getReason().contains("처리 결과 코드가 없습니다")); assertFalse(missing.getReason().contains("코드 )"));
            payload.set("<html><body>maintenance</body></html>");
            assertTrue(assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null)).getReason().contains("해석하지 못했습니다"));
        } finally { server.stop(0); }
    }
    @Test void upstream503IsReportedAsServiceUnavailableRatherThanBadCredentials() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/getBrTitleInfo",e -> { e.sendResponseHeaders(503,-1); e.close(); });
        server.start();
        try {
            var client=new BuildingLedgerClient(new ObjectMapper(),"test-key","http://127.0.0.1:"+server.getAddress().getPort());
            var error=assertThrows(ResponseStatusException.class,()->client.fetch("getBrTitleInfo",query,null,null));
            assertTrue(error.getReason().contains("HTTP 503")); assertFalse(error.getReason().contains("인증"));
        } finally { server.stop(0); }
    }
    @Test void xmlExternalEntitiesAreRejected() {
        var client=new BuildingLedgerClient(new ObjectMapper(),"test-key","http://127.0.0.1:1");
        assertThrows(ResponseStatusException.class,()->client.parseResponse("<!DOCTYPE response [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><response><header><resultCode>&xxe;</resultCode></header></response>"));
    }
}
