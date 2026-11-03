package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
}
