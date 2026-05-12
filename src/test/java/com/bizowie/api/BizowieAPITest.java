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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private BizowieAPI client(boolean v2) {
        return BizowieAPI.builder()
                .apiKey("ak")
                .secretKey("sk")
                .site("example.invalid")
                .v2(v2)
                .httpFactory(localFactory())
                .build();
    }

    @Test
    void missingMethodThrows() {
        BizowieAPI bz = client(false);
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
    void v1SendsMultipart() {
        responseBody = "{\"success\":1,\"id\":42}";
        Map<String, Object> params = new HashMap<>();
        params.put("comment", "hi");

        BizowieAPIResponse r = client(false).call("databases/add_note/1/2/3", params);

        RecordedRequest req = recorded.get();
        assertEquals("/bz/api/databases/add_note/1/2/3", req.path);
        assertEquals("POST", req.method);
        assertNotNull(req.contentType);
        assertTrue(req.contentType.startsWith("multipart/form-data; boundary="), req.contentType);
        String body = new String(req.body, StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"api_key\""));
        assertTrue(body.contains("name=\"secret_key\""));
        assertTrue(body.contains("name=\"site\""));
        assertTrue(body.contains("name=\"request\""));
        assertTrue(body.contains("\"comment\":\"hi\""));
        assertEquals("Bizowie::API", req.userAgent);

        assertTrue(r.isSuccess());
        assertEquals(42, ((Number) r.getData().get("id")).intValue());
        assertFalse(r.getData().containsKey("success"));
    }

    @Test
    void v2SendsJsonBodyWithFormDataContentType() {
        responseBody = "{\"success\":true,\"ok\":1}";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("foo", "bar");

        BizowieAPIResponse r = client(true).call("things/list", params);

        RecordedRequest req = recorded.get();
        assertEquals("/bz/apiv2/call/things/list", req.path);
        assertEquals("form-data", req.contentType);
        String body = new String(req.body, StandardCharsets.UTF_8);
        assertTrue(body.startsWith("{"), body);
        assertTrue(body.contains("\"api_key\":\"ak\""));
        assertTrue(body.contains("\"secret_key\":\"sk\""));
        assertTrue(body.contains("\"api_version\":\"1.00\""));
        assertTrue(body.contains("\"foo\":\"bar\""));
        assertTrue(r.isSuccess());
    }

    @Test
    void v2UsesCustomApiVersion() {
        responseBody = "{\"success\":1}";
        BizowieAPI bz = BizowieAPI.builder()
                .apiKey("ak").secretKey("sk").site("example.invalid")
                .v2(true).apiVersion("2.50")
                .httpFactory(localFactory())
                .build();
        bz.call("ping", null);
        String body = new String(recorded.get().body, StandardCharsets.UTF_8);
        assertTrue(body.contains("\"api_version\":\"2.50\""));
    }

    @Test
    void unprocessedFlagOnBadJson() {
        responseBody = "not json at all";
        BizowieAPIResponse r = client(false).call("anything", null);
        assertFalse(r.isSuccess());
        assertEquals(1, ((Number) r.getData().get("unprocessed")).intValue());
    }

    @Test
    void successFalseWhenAbsent() {
        responseBody = "{\"hello\":\"world\"}";
        BizowieAPIResponse r = client(false).call("anything", null);
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
