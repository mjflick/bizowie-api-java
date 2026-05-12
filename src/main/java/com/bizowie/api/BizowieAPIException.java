package com.bizowie.api;

/**
 * Unchecked exception raised by the Bizowie API client for fatal failures.
 *
 * <p>Used for two distinct categories of error:
 * <ul>
 *   <li><b>Configuration / argument errors</b> &mdash; missing credentials at
 *       build time, or an empty {@code method} passed to
 *       {@link BizowieAPI#call(String, java.util.Map)}.</li>
 *   <li><b>Transport errors</b> &mdash; anything thrown while opening the
 *       connection, writing the request body, or reading the response. The
 *       original {@link java.io.IOException} is preserved as the
 *       {@linkplain #getCause() cause}.</li>
 * </ul>
 *
 * <p>Application-level errors reported by the Bizowie server (i.e. a
 * well-formed JSON response containing {@code "success": false}) are
 * <em>not</em> raised as exceptions; inspect
 * {@link BizowieAPIResponse#isSuccess()} instead.
 */
public class BizowieAPIException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BizowieAPIException(String message) {
        super(message);
    }

    public BizowieAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
