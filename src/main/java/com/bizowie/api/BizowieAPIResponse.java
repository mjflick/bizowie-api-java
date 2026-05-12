package com.bizowie.api;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a {@link BizowieAPI#call(String, java.util.Map)} invocation.
 *
 * <p>Instances are immutable. The {@code success} flag is lifted out of the
 * raw response body so callers can branch on it directly; everything else the
 * server returned is left intact in {@link #getData()}.
 *
 * <p>Note: this class only describes the application-level outcome reported
 * by the Bizowie API. Transport-level failures (network errors, malformed
 * URLs, etc.) surface as {@link BizowieAPIException} rather than as a
 * response with {@code success == false}.
 */
public final class BizowieAPIResponse {
    private final Map<String, Object> data;
    private final boolean success;

    /**
     * @param data response payload with the {@code success} field already removed
     *             (may be {@code null}, treated as empty)
     * @param success whether the call succeeded
     */
    public BizowieAPIResponse(Map<String, Object> data, boolean success) {
        this.data = data == null ? Collections.emptyMap() : data;
        this.success = success;
    }

    /**
     * Decoded JSON body returned by the API, with the {@code success} field
     * stripped. When the response body could not be parsed as JSON, this map
     * contains a single entry, {@code {"unprocessed": 1}}.
     *
     * <p>The returned map is the same instance passed to the constructor and
     * may be either mutable or unmodifiable; callers should not assume either.
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * {@code true} if the API reported a truthy {@code success} field.
     * Bizowie historically returns either {@code 1}/{@code 0} or
     * {@code true}/{@code false}; both shapes are accepted.
     */
    public boolean isSuccess() {
        return success;
    }
}
