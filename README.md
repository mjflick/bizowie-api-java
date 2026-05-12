# Bizowie API (Java)

Java client for the [Bizowie ERP](https://bizowie.com) API. Port of the Perl
module [`WWW::Bizowie::API`](https://metacpan.org/pod/WWW::Bizowie::API).

* Minimum Java version: **8**
* One runtime dependency: `jackson-databind`
* Supports both the v1 (multipart) and v2 (JSON) Bizowie API endpoints
* Builder-based construction, no reflection / annotations

## Install

### Maven

```xml
<dependency>
    <groupId>com.bizowie</groupId>
    <artifactId>bizowie-api</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("com.bizowie:bizowie-api:0.5.0")
```

## Usage

```java
import com.bizowie.api.BizowieAPI;
import com.bizowie.api.BizowieAPIResponse;

import java.util.Map;

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

### Using the v2 endpoint

```java
BizowieAPI bz = BizowieAPI.builder()
    .apiKey("...")
    .secretKey("...")
    .site("mysite.bizowie.com")
    .v2(true)
    .apiVersion("1.00")  // optional, defaults to "1.00"
    .build();
```

### Builder options

| Method            | Description                                                            |
|-------------------|------------------------------------------------------------------------|
| `apiKey(String)`  | Your Bizowie API key. *Required.*                                      |
| `secretKey(String)` | Your Bizowie secret key. *Required.*                                 |
| `site(String)`    | The hostname of your Bizowie instance. *Required.*                     |
| `v2(boolean)`     | Route calls through the v2 endpoint (`/bz/apiv2/call/`).               |
| `apiVersion(String)` | API version sent with v2 requests. Defaults to `"1.00"`.            |
| `debug(boolean)`  | Print raw response bodies to stderr when JSON decoding fails.          |
| `objectMapper(ObjectMapper)` | Provide a custom Jackson `ObjectMapper`.                    |

### Responses

`BizowieAPIResponse` exposes:

* `boolean isSuccess()` &mdash; the `success` field, removed from `data`
* `Map<String, Object> getData()` &mdash; the remaining decoded JSON body

If the response cannot be decoded as JSON, `getData()` returns `{"unprocessed": 1}` and `isSuccess()` is `false`.

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
