package com.bizowie.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Java client for the Bizowie ERP API.
 *
 * <pre>{@code
 * BizowieAPI bz = BizowieAPI.builder()
 *     .apiKey("02cc7058-cd22-4c8e-ad7c-a8f3f2a64bd0")
 *     .secretKey("58c57abc-1e16-3571-bb35-73876bcef746")
 *     .site("mysite.bizowie.com")
 *     .build();
 *
 * Map<String, Object> params = new HashMap<>();
 * params.put("comment", "I added this comment via the API!");
 * BizowieAPIResponse r = bz.call("databases/add_note/3/10/123", params);
 * }</pre>
 */
public final class BizowieAPI {
    private static final String USER_AGENT = "Bizowie::API";
    private static final String DEFAULT_API_VERSION = "1.00";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final String apiKey;
    private final String secretKey;
    private final String site;
    private final boolean v2;
    private final String apiVersion;
    private final boolean debug;
    private final ObjectMapper mapper;
    private final HttpFactory httpFactory;

    private BizowieAPI(Builder b) {
        if (b.site == null || b.site.isEmpty()) {
            throw new BizowieAPIException("site not specified");
        }
        if (b.apiKey == null || b.apiKey.isEmpty()) {
            throw new BizowieAPIException("api_key not specified");
        }
        if (b.secretKey == null || b.secretKey.isEmpty()) {
            throw new BizowieAPIException("secret_key not specified");
        }
        this.apiKey = b.apiKey;
        this.secretKey = b.secretKey;
        this.site = b.site;
        this.v2 = b.v2;
        this.apiVersion = b.apiVersion == null ? DEFAULT_API_VERSION : b.apiVersion;
        this.debug = b.debug;
        this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
        this.httpFactory = b.httpFactory != null ? b.httpFactory : DefaultHttpFactory.INSTANCE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Make a Bizowie API call.
     *
     * @param method API path, e.g. {@code "databases/add_note/3/10/123"}
     * @param params request parameters (may be null)
     */
    public BizowieAPIResponse call(String method, Map<String, Object> params) {
        if (method == null || method.isEmpty()) {
            throw new BizowieAPIException("[Bizowie::API] fatal error: no method given");
        }
        return v2 ? callV2(method, params) : callV1(method, params);
    }

    private BizowieAPIResponse callV1(String method, Map<String, Object> params) {
        try {
            String json = mapper.writeValueAsString(params != null ? params : new HashMap<String, Object>());
            String boundary = "----BizowieAPI" + UUID.randomUUID().toString().replace("-", "");

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("api_key", apiKey);
            fields.put("secret_key", secretKey);
            fields.put("site", site);
            fields.put("request", json);

            byte[] body = buildMultipartBody(boundary, fields);

            HttpURLConnection conn = httpFactory.open("https://" + site + "/bz/api/" + method);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setFixedLengthStreamingMode(body.length);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            return parseResponse(conn);
        } catch (IOException e) {
            throw new BizowieAPIException("HTTP request failed", e);
        }
    }

    private BizowieAPIResponse callV2(String method, Map<String, Object> params) {
        try {
            Map<String, Object> body = params == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(params);
            body.put("api_key", apiKey);
            body.put("secret_key", secretKey);
            if (!body.containsKey("api_version")) {
                body.put("api_version", apiVersion);
            }
            byte[] bytes = mapper.writeValueAsBytes(body);

            HttpURLConnection conn = httpFactory.open("https://" + site + "/bz/apiv2/call/" + method);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "form-data");
            conn.setFixedLengthStreamingMode(bytes.length);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
            return parseResponse(conn);
        } catch (IOException e) {
            throw new BizowieAPIException("HTTP request failed", e);
        }
    }

    private BizowieAPIResponse parseResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream stream = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
        byte[] raw = stream == null ? new byte[0] : readAll(stream);
        String text = new String(raw, StandardCharsets.UTF_8);

        Map<String, Object> data;
        boolean success;
        try {
            data = mapper.readValue(text, MAP_TYPE);
            success = toBool(data.remove("success"));
        } catch (IOException ex) {
            if (debug) {
                System.err.println("[Bizowie::API] HTTP " + status + ": " + text);
            }
            data = new LinkedHashMap<>();
            data.put("unprocessed", 1);
            success = false;
        }
        return new BizowieAPIResponse(data, success);
    }

    private static boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        String s = value.toString();
        return !(s.isEmpty() || "0".equals(s) || "false".equalsIgnoreCase(s));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static byte[] buildMultipartBody(String boundary, Map<String, String> fields) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            baos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    /** Hook for injecting a mock transport in tests. */
    public interface HttpFactory {
        HttpURLConnection open(String url) throws IOException;
    }

    private static final class DefaultHttpFactory implements HttpFactory {
        static final DefaultHttpFactory INSTANCE = new DefaultHttpFactory();

        @Override
        public HttpURLConnection open(String url) throws IOException {
            return (HttpURLConnection) new URL(url).openConnection();
        }
    }

    public static final class Builder {
        private String apiKey;
        private String secretKey;
        private String site;
        private boolean v2;
        private String apiVersion;
        private boolean debug;
        private ObjectMapper mapper;
        private HttpFactory httpFactory;

        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder secretKey(String v) { this.secretKey = v; return this; }
        public Builder site(String v) { this.site = v; return this; }
        public Builder v2(boolean v) { this.v2 = v; return this; }
        public Builder apiVersion(String v) { this.apiVersion = v; return this; }
        public Builder debug(boolean v) { this.debug = v; return this; }
        public Builder objectMapper(ObjectMapper m) { this.mapper = m; return this; }
        public Builder httpFactory(HttpFactory f) { this.httpFactory = f; return this; }

        public BizowieAPI build() {
            return new BizowieAPI(this);
        }
    }
}
