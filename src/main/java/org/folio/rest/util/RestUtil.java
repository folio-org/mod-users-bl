package org.folio.rest.util;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

import java.util.Map;

/**
 * Util class with static method for sending http request
 */
public class RestUtil {
  public static class WrappedResponse {
    private int code;
    private String body;
    private JsonObject json;
    private HttpClientResponse response;

    WrappedResponse(int code, String body,
                    HttpClientResponse response) {
      this.code = code;
      this.body = body;
      this.response = response;
      try {
        json = new JsonObject(body);
      } catch (Exception e) {
        json = null;
      }
    }

    public int getCode() {
      return code;
    }

    public String getBody() {
      return body;
    }

    public HttpClientResponse getResponse() {
      return response;
    }

    public JsonObject getJson() {
      return json;
    }
  }

  private RestUtil() {
  }

  /**
   * Create http request
   *
   * @param client  - vertx http client
   * @param url     - url for http request
   * @param method  - http method
   * @param headers - map with request's headers
   * @param payload - body of request
   * @return - async http response
   */
  public static Future<WrappedResponse> doRequest(HttpClient client, String url, HttpMethod method,
                                                  MultiMap headers, String payload) {
    Promise<WrappedResponse> promise = Promise.promise();
    try {
      HttpClientRequest request = client.requestAbs(method, url);
      if (headers != null) {
        headers.add("Content-type", "application/json")
          .add("Accept", "application/json, text/plain");
        for (Map.Entry entry : headers.entries()) {
          if (entry.getValue() != null) {
            request.putHeader((String) entry.getKey(), (String) entry.getValue());
          }
        }
      }
      request.exceptionHandler(promise::fail);
      request.handler(req -> req.bodyHandler(buf -> {
        WrappedResponse wr = new WrappedResponse(req.statusCode(), buf.toString(), req);
        promise.complete(wr);
      }));
      if (method == HttpMethod.PUT || method == HttpMethod.POST) {
        request.end(payload);
      } else {
        request.end();
      }
      return promise.future();
    } catch (Exception e) {
      promise.fail(e);
      return promise.future();
    }
  }
}
