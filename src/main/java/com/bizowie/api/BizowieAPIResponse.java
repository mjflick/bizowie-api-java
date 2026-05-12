package com.bizowie.api;

import java.util.Collections;
import java.util.Map;

/** Result of a Bizowie API call. */
public final class BizowieAPIResponse {
    private final Map<String, Object> data;
    private final boolean success;

    public BizowieAPIResponse(Map<String, Object> data, boolean success) {
        this.data = data == null ? Collections.emptyMap() : data;
        this.success = success;
    }

    /** Decoded JSON body returned by the API, with the {@code success} field stripped. */
    public Map<String, Object> getData() {
        return data;
    }

    /** True if the API reported {@code success: true} (or any truthy value). */
    public boolean isSuccess() {
        return success;
    }
}
