package com.bizowie.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BizowieAPITest {

    private HttpServer server;
    private int port;
    private final AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
    private volatile String responseBody = "{\"success\":1,\"hello\":\"world\"}";
    private volatile int responseStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        RecordedRequest req = new RecordedRequest();
        req.path = ex.getRequestURI().getPath();
        req.method = ex.getRequestMethod();
        req.contentType = ex.getRequestHeaders().getFirst("Content-Type");
        req.userAgent = ex.getRequestHeaders().getFirst("User-Agent");
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
            req.body = baos.toByteArray();
        }
        recorded.set(req);
        byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(responseStatus, resp.length);
        try (java.io.OutputStream out = ex.getResponseBody()) {
            out.write(resp);
        }
    }

    private BizowieAPI.HttpFactory localFactory() {
        return (url) -> {
            URI uri = URI.create(url);
            URL rewritten = new URL("http", "127.0.0.1", port, uri.getRawPath());
            return (HttpURLConnection) rewritten.openConnection();
        };
    }

    private BizowieAPI client() {
        return BizowieAPI.builder()
                .apiKey("ak")
                .secretKey("sk")
                .site("example.invalid")
                .httpFactory(localFactory())
                .build();
    }

    @Test
    void missingMethodThrows() {
        BizowieAPI bz = client();
        BizowieAPIException ex = assertThrows(BizowieAPIException.class, () -> bz.call("", null));
        assertEquals("[Bizowie::API] fatal error: no method given", ex.getMessage());
    }

    @Test
    void missingCredentialsThrows() {
        BizowieAPIException ex = assertThrows(BizowieAPIException.class, () ->
                BizowieAPI.builder().apiKey("x").secretKey("y").build());
        assertTrue(ex.getMessage().contains("site"));
    }

    @Test
    void sendsJsonBodyWithFormDataContentType() {
        responseBody = "{\"success\":true,\"ok\":1}";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("foo", "bar");

        BizowieAPIResponse r = client().call("things/list", params);

        RecordedRequest req = recorded.get();
        assertEquals("/bz/apiv2/call/things/list", req.path);
        assertEquals("POST", req.method);
        assertEquals("form-data", req.contentType);
        assertEquals("Bizowie::API", req.userAgent);
        String body = new String(req.body, StandardCharsets.UTF_8);
        assertTrue(body.startsWith("{"), body);
        assertTrue(body.contains("\"api_key\":\"ak\""));
        assertTrue(body.contains("\"secret_key\":\"sk\""));
        assertTrue(body.contains("\"api_version\":\"1.00\""));
        assertTrue(body.contains("\"foo\":\"bar\""));
        assertTrue(r.isSuccess());
        assertEquals(1, ((Number) r.getData().get("ok")).intValue());
        assertFalse(r.getData().containsKey("success"));
    }

    @Test
    void usesCustomApiVersion() {
        responseBody = "{\"success\":1}";
        BizowieAPI bz = BizowieAPI.builder()
                .apiKey("ak").secretKey("sk").site("example.invalid")
                .apiVersion("2.50")
                .httpFactory(localFactory())
                .build();
        bz.call("ping", null);
        String body = new String(recorded.get().body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"api_version\":\"2.50\""));
    }

    @Test
    void perCallApiVersionWins() {
        responseBody = "{\"success\":1}";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("api_version", "9.99");
        client().call("ping", params);
        String body = new String(recorded.get().body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"api_version\":\"9.99\""), body);
        assertFalse(body.contains("\"api_version\":\"1.00\""), body);
    }

    @Test
    void unprocessedFlagOnBadJson() {
        responseBody = "not json at all";
        BizowieAPIResponse r = client().call("anything", null);
        assertFalse(r.isSuccess());
        assertEquals(1, ((Number) r.getData().get("unprocessed")).intValue());
    }

    @Test
    void successFalseWhenAbsent() {
        responseBody = "{\"hello\":\"world\"}";
        BizowieAPIResponse r = client().call("anything", null);
        assertFalse(r.isSuccess());
        assertEquals("world", r.getData().get("hello"));
    }

    private static final class RecordedRequest {
        String path;
        String method;
        String contentType;
        String userAgent;
        byte[] body;
    }
}
