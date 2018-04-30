package org.folio.rest;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import java.util.Map;

/**
 *
 * @author kurt
 */
public class TestUtil {
  class WrappedResponse {
    private int code;
    private String body;
    private JsonObject json;
    private HttpClientResponse response;

    public WrappedResponse(int code, String body, HttpClientResponse response) {
      this.code = code;
      this.body = body;
      this.response = response;
      try {
        json = new JsonObject(body);
      } catch(Exception e) {
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

  public Future<WrappedResponse> doRequest(Vertx vertx, String url, HttpMethod method, MultiMap headers, String payload) {
    System.out.println(String.format("Sending %s request to endpoint %s with payload %s\n", method.toString(), url, payload));
    Future<WrappedResponse> future = Future.future();
    boolean addPayLoad = false;
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest request = client.requestAbs(method, url);
    //Add standard headers
    request.putHeader("X-Okapi-Tenant", "diku")
            .putHeader("content-type", "application/json")
            .putHeader("accept", "application/json");
    if(headers != null) {
      for(Map.Entry entry : headers.entries()) {
        request.putHeader((String)entry.getKey(), (String)entry.getValue());
      }
    }
    //standard exception handler
    request.exceptionHandler(e -> { future.fail(e); });
    request.handler( req -> {
      req.bodyHandler(buf -> {
        WrappedResponse wr = new WrappedResponse(req.statusCode(), buf.toString(), req);
        future.complete(wr);
      });
    });
    if(method == HttpMethod.PUT || method == HttpMethod.POST) {
      request.end(payload);
    } else {
      request.end();
    }
    return future;
  }
}


