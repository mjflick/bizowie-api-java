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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java client for the Bizowie ERP JSON API.
 *
 * <h2>Overview</h2>
 *
 * Instances are constructed through {@link #builder()} and are immutable and
 * thread-safe once built &mdash; a single {@code BizowieAPI} can be shared
 * across threads and reused for the lifetime of the application. Each call to
 * {@link #call(String, Map)} opens its own {@link HttpURLConnection}; there is
 * no internal connection pool.
 *
 * <h2>Authentication</h2>
 *
 * Every request includes the configured {@code api_key} and {@code secret_key}
 * in the JSON body, alongside an {@code api_version} that defaults to
 * {@value #DEFAULT_API_VERSION}. Caller-supplied parameters never overwrite
 * these credentials, but a caller <em>may</em> override {@code api_version} on
 * a per-call basis by including it in {@code params}.
 *
 * <h2>Transport</h2>
 *
 * Requests are sent as {@code POST} to {@code https://<site>/bz/apiv2/call/<method>}
 * with a JSON body and the {@code Content-Type: form-data} header that the
 * Bizowie endpoint expects. HTTPS is always used; the {@code site} value must
 * be a hostname (no scheme).
 *
 * <h2>Example</h2>
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
 *
 * if (r.isSuccess()) {
 *     System.out.println(r.getData());
 * }
 * }</pre>
 *
 * @see BizowieAPIResponse
 * @see BizowieAPIException
 */
public final class BizowieAPI {
    private static final String USER_AGENT = "Bizowie::API";

    /** Default {@code api_version} value sent with every request when not overridden. */
    public static final String DEFAULT_API_VERSION = "1.00";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final String apiKey;
    private final String secretKey;
    private final String site;
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
        this.apiVersion = b.apiVersion == null ? DEFAULT_API_VERSION : b.apiVersion;
        this.debug = b.debug;
        this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
        this.httpFactory = b.httpFactory != null ? b.httpFactory : DefaultHttpFactory.INSTANCE;
    }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Invoke a Bizowie API method and return its decoded response.
     *
     * <p>The {@code method} string is the API path relative to
     * {@code /bz/apiv2/call/}, including any positional ID segments, for
     * example {@code "databases/add_note/3/10/123"}. The leading slash is
     * implicit; do not include it.
     *
     * <p>{@code params} is serialized to JSON, merged with the configured
     * credentials, and posted as the request body. {@code null} is treated
     * as an empty map. Values may be any type the configured Jackson
     * {@link ObjectMapper} can serialize.
     *
     * <p>Network and JSON encoding failures are wrapped in
     * {@link BizowieAPIException}. A successful HTTP exchange whose body is
     * not valid JSON is <em>not</em> an exception &mdash; it is reported as
     * {@code isSuccess() == false} with {@code data == {"unprocessed": 1}}.
     *
     * @param method API path (must be non-{@code null} and non-empty)
     * @param params request parameters, may be {@code null}
     * @return the decoded response
     * @throws BizowieAPIException if {@code method} is empty, or the request
     *         fails at the transport layer
     */
    public BizowieAPIResponse call(String method, Map<String, Object> params) {
        if (method == null || method.isEmpty()) {
            throw new BizowieAPIException("[Bizowie::API] fatal error: no method given");
        }
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

    /**
     * SPI hook for substituting the underlying HTTP transport. Primarily
     * intended for tests, where it allows redirecting requests to a local
     * stub server, but also useful for callers that need custom TLS,
     * proxies, or timeouts on the returned {@link HttpURLConnection}.
     */
    public interface HttpFactory {
        /** Open a connection for the given fully-qualified URL. */
        HttpURLConnection open(String url) throws IOException;
    }

    private static final class DefaultHttpFactory implements HttpFactory {
        static final DefaultHttpFactory INSTANCE = new DefaultHttpFactory();

        @Override
        public HttpURLConnection open(String url) throws IOException {
            return (HttpURLConnection) new URL(url).openConnection();
        }
    }

    /**
     * Fluent builder for {@link BizowieAPI}. {@code apiKey}, {@code secretKey},
     * and {@code site} are required; the remaining options have sensible
     * defaults. Validation runs in {@link #build()}.
     */
    public static final class Builder {
        private String apiKey;
        private String secretKey;
        private String site;
        private String apiVersion;
        private boolean debug;
        private ObjectMapper mapper;
        private HttpFactory httpFactory;

        /** Bizowie-issued API key (UUID). Required. */
        public Builder apiKey(String v) { this.apiKey = v; return this; }

        /** Bizowie-issued secret key (UUID). Required. */
        public Builder secretKey(String v) { this.secretKey = v; return this; }

        /** Hostname of the Bizowie tenant, e.g. {@code "mysite.bizowie.com"}. Required. */
        public Builder site(String v) { this.site = v; return this; }

        /**
         * Override the {@code api_version} sent on every request. Defaults to
         * {@link #DEFAULT_API_VERSION}. Individual calls can still override
         * this by including {@code api_version} in their parameter map.
         */
        public Builder apiVersion(String v) { this.apiVersion = v; return this; }

        /**
         * When {@code true}, log raw response bodies to {@code System.err}
         * whenever JSON decoding fails. Off by default.
         */
        public Builder debug(boolean v) { this.debug = v; return this; }

        /**
         * Supply a custom Jackson {@link ObjectMapper}. Useful for configuring
         * non-default serialization (e.g. for {@code java.time} types). A
         * fresh default {@code ObjectMapper} is used if not provided.
         */
        public Builder objectMapper(ObjectMapper m) { this.mapper = m; return this; }

        /** Replace the HTTP transport. See {@link HttpFactory}. */
        public Builder httpFactory(HttpFactory f) { this.httpFactory = f; return this; }

        /**
         * Build the client.
         *
         * @throws BizowieAPIException if {@code apiKey}, {@code secretKey}, or
         *         {@code site} is missing.
         */
        public BizowieAPI build() {
            return new BizowieAPI(this);
        }
    }
}
