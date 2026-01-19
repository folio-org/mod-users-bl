package org.folio.rest.util;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;


import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Util class with static method for sending http request
 */
public class RestUtil {
  public static class WrappedResponse {
    private int code;
    private String body;
    private JsonObject json;
    private HttpResponse<Buffer> response;

    public WrappedResponse(int code, String body,
                    HttpResponse<Buffer> response) {
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

    public HttpResponse<Buffer> getResponse() {
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
  public static Future<WrappedResponse> doRequest(HttpClient client, String url,
    HttpMethod method, MultiMap headers, String payload) {

    WebClient webClient = WebClient.wrap(client);

    Promise<WrappedResponse> promise = Promise.promise();

    HttpRequest<Buffer> request = webClient.requestAbs(method, url);
    if (headers != null) {
      headers.add("Content-type", "application/json")
        .add("Accept", "application/json, text/plain");

      for (Map.Entry<String, String> entry : headers.entries()) {
        if (entry.getValue() != null) {
          request.putHeader(entry.getKey(), entry.getValue());
        }
      }
    }

    var buffer = StringUtils.isEmpty(payload) ? null : Buffer.buffer(payload);

    Future<HttpResponse<Buffer>> response = request.sendBuffer(buffer);

    response.onSuccess(res -> {
      WrappedResponse wr = new WrappedResponse(res.statusCode(), res.bodyAsString(), res);
      promise.complete(wr);
    });
    response.onFailure(promise::fail);

    return promise.future();
  }
}
