# Bizowie API (Java)

Java client for the [Bizowie ERP](https://bizowie.com) API. Port of the Perl
module [`WWW::Bizowie::API`](https://metacpan.org/pod/WWW::Bizowie::API).

* Minimum Java version: **8**
* One runtime dependency: `jackson-databind`
* Targets the Bizowie v2 (JSON) endpoint at `/bz/apiv2/call/`
* Builder-based construction, no reflection / annotations
* Thread-safe: a single client can be shared across threads

## Install

### Maven

```xml
<dependency>
    <groupId>com.bizowie</groupId>
    <artifactId>bizowie-api</artifactId>
    <version>0.6.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("com.bizowie:bizowie-api:0.6.0")
```

## Quick start

```java
import com.bizowie.api.BizowieAPI;
import com.bizowie.api.BizowieAPIResponse;

import java.util.Collections;

BizowieAPI bz = BizowieAPI.builder()
    .apiKey("02cc7058-cd22-4c8e-ad7c-a8f3f2a64bd0")
    .secretKey("58c57abc-1e16-3571-bb35-73876bcef746")
    .site("mysite.bizowie.com")
    .build();

BizowieAPIResponse r = bz.call(
    "databases/add_note/3/10/123",
    Collections.singletonMap("comment", "I added this comment via the API!")
);

if (r.isSuccess()) {
    System.out.println(r.getData());
}
```

## Configuration

### Builder options

| Method                       | Description                                                                                  |
|------------------------------|----------------------------------------------------------------------------------------------|
| `apiKey(String)`             | Bizowie-issued API key (UUID). **Required.**                                                 |
| `secretKey(String)`          | Bizowie-issued secret key (UUID). **Required.**                                              |
| `site(String)`               | Hostname of your Bizowie instance, e.g. `mysite.bizowie.com` (no scheme). **Required.**      |
| `apiVersion(String)`         | `api_version` field sent with every request. Defaults to `"1.00"`.                           |
| `debug(boolean)`             | Print raw response bodies to stderr when JSON decoding fails. Off by default.                |
| `objectMapper(ObjectMapper)` | Provide a custom Jackson `ObjectMapper` (e.g. with `JavaTimeModule` registered).             |
| `httpFactory(HttpFactory)`   | Replace the underlying transport. Useful for tests, custom TLS, proxies, or timeouts.        |

### Overriding `api_version` per-call

The builder's `apiVersion` value is the default. Any call may override it by
including `api_version` in the parameter map:

```java
Map<String, Object> params = new HashMap<>();
params.put("api_version", "2.50");
bz.call("things/list", params);
```

## Requests

Each `call(method, params)` translates to:

* **URL** &mdash; `https://<site>/bz/apiv2/call/<method>` (HTTPS only)
* **Method** &mdash; `POST`
* **Headers** &mdash; `User-Agent: Bizowie::API`, `Content-Type: form-data`
* **Body** &mdash; JSON object: your `params` merged with `api_key`,
  `secret_key`, and `api_version`

The `method` argument is the API path relative to `/bz/apiv2/call/` and may
include positional segments, e.g. `"databases/add_note/3/10/123"`. Do not
include a leading slash. `params` may be `null` (treated as empty).

Caller-supplied parameters never overwrite `api_key` or `secret_key`, but
they *can* override `api_version` (see above).

## Responses

`BizowieAPIResponse` exposes:

* `boolean isSuccess()` &mdash; the `success` field of the response, accepted
  as either a JSON boolean or `1`/`0`. Removed from `getData()`.
* `Map<String, Object> getData()` &mdash; the rest of the decoded JSON body.

If the response cannot be decoded as JSON, `getData()` returns
`{"unprocessed": 1}` and `isSuccess()` is `false`. Enable `debug(true)` on the
builder to dump the raw body to stderr when this happens.

## Error handling

* **Application errors** &mdash; the server returned valid JSON but with
  `success: false` (or absent). Surface as a normal `BizowieAPIResponse`
  whose `isSuccess()` is `false`; inspect `getData()` for details.
* **Transport / config errors** &mdash; missing credentials, empty `method`,
  network failures, malformed URLs, etc. Thrown as `BizowieAPIException`
  (unchecked); the underlying `IOException`, if any, is preserved as the
  cause.

```java
try {
    BizowieAPIResponse r = bz.call("things/list", null);
    if (!r.isSuccess()) {
        log.warn("API call rejected: {}", r.getData());
    }
} catch (BizowieAPIException e) {
    log.error("API call failed at transport layer", e);
}
```

## Customizing transport

`HttpFactory` is an SPI for substituting the underlying HTTP transport. It
receives a fully-qualified URL and must return an open `HttpURLConnection`.
Use it to:

* Route requests through a proxy
* Inject TLS configuration or a custom `SSLSocketFactory`
* Apply connect / read timeouts
* Redirect to a stub server in tests

```java
BizowieAPI bz = BizowieAPI.builder()
    .apiKey("...").secretKey("...").site("mysite.bizowie.com")
    .httpFactory(url -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5_000);
        c.setReadTimeout(30_000);
        return c;
    })
    .build();
```

## Thread safety & lifecycle

`BizowieAPI` instances are immutable after `build()` and safe to share across
threads. Build one per (site, credential) pair at application startup and
reuse it. There is no `close()` &mdash; each `call` opens its own
`HttpURLConnection` and there is no internal connection pool.

## Build / Test

Requires Maven 3.6+ and JDK 8+.

```bash
mvn test
mvn package
```

## Publishing to Maven Central

This project is configured to publish via the
[Sonatype Central Publisher Portal](https://central.sonatype.org/publish/publish-portal-maven/),
which replaced OSSRH in 2024.

### One-time setup

1. **Verify your namespace.** Either:
   * Verify ownership of `com.bizowie` by adding a DNS TXT record to `bizowie.com`, *or*
   * Change `<groupId>` to `io.github.<your-github-username>` (auto-verified for GitHub namespaces).
2. **Create a Central Publisher Portal account** at https://central.sonatype.com and generate a User Token (Account → Generate User Token).
3. **Add credentials to `~/.m2/settings.xml`:**

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>YOUR_TOKEN_USERNAME</username>
         <password>YOUR_TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

4. **Set up GPG signing.** Maven Central requires every artifact to be PGP-signed.
   ```bash
   gpg --gen-key
   gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
   ```

### Release

```bash
mvn -Prelease deploy
```

The `release` profile signs the JARs with GPG and uploads via
`central-publishing-maven-plugin`, which auto-publishes when validation passes.

### Snapshot releases

The Central Portal does not currently support `-SNAPSHOT` deployments &mdash;
use GitHub Packages or a private repository for snapshots.

## License

Dual-licensed under the **Artistic License 1.0 (Perl)** *or* **GPL 1.0 or later**, matching the original Perl module's `same terms as Perl itself` clause.
