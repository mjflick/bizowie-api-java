/**
 * Java client for the Bizowie ERP JSON API.
 *
 * <p>The entry point is {@link com.bizowie.api.BizowieAPI}, constructed via
 * {@link com.bizowie.api.BizowieAPI#builder()}. All calls go through the
 * Bizowie {@code /bz/apiv2/call/} JSON endpoint; the legacy multipart v1
 * endpoint is no longer supported.
 *
 * <p>Typical usage:
 * <pre>{@code
 * BizowieAPI bz = BizowieAPI.builder()
 *     .apiKey("…")
 *     .secretKey("…")
 *     .site("mysite.bizowie.com")
 *     .build();
 *
 * BizowieAPIResponse r = bz.call("things/list", null);
 * if (r.isSuccess()) {
 *     handle(r.getData());
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * {@link com.bizowie.api.BizowieAPI} instances are immutable and safe to
 * share across threads.
 *
 * <h2>Error handling</h2>
 * Configuration and transport failures throw
 * {@link com.bizowie.api.BizowieAPIException}. Application-level failures
 * (i.e. the server returning {@code success: false}) are reported through
 * {@link com.bizowie.api.BizowieAPIResponse#isSuccess()}.
 */
package com.bizowie.api;
