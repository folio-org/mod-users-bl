package org.folio.rest.util;

import static org.folio.rest.util.EnvUtils.getEnvOrDefault;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HttpClientUtil {
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";
  private static final Map<Vertx, HttpClient> HTTP_CLIENT_CACHE = new ConcurrentHashMap<>();

  private HttpClientUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  public static HttpClient getInstance(Vertx vertx) {
    return HTTP_CLIENT_CACHE.computeIfAbsent(vertx, HttpClientUtil::initHttpClient);
  }

  private static HttpClient initHttpClient(Vertx vertx) {
    int lookupTimeout = getEnvOrDefault(
      LOOKUP_TIMEOUT, "LOOKUP_TIMEOUT", Integer.parseInt(LOOKUP_TIMEOUT_VAL), Integer::parseInt);
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    return vertx.createHttpClient(options);
  }
}
