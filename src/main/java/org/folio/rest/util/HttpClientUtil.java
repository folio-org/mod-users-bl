package org.folio.rest.util;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.folio.rest.RestVerticle;

public class HttpClientUtil {
  private static final String KEY = "httpClient";

  public static HttpClient getInstance(Vertx vertx) {
    var context = vertx.getOrCreateContext();
    var httpClient = (HttpClient) context.getLocal(KEY);
    if (httpClient == null) {
      httpClient = initHttpClient(vertx);
      context.putLocal(KEY, httpClient);
    }
    return httpClient;
  }

  private static HttpClient initHttpClient(Vertx vertx) {
    int lookupTimeout = Integer
      .parseInt(RestVerticle.MODULE_SPECIFIC_ARGS.getOrDefault("lookup.timeout", "1000"));
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    return vertx.createHttpClient(options);
  }
}
