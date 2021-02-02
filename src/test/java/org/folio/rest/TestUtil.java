package org.folio.rest;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class TestUtil {

  private static final Logger log = LogManager.getLogger(TestUtil.class);

  class WrappedResponse {
    private int code;
    private String body;
    private JsonObject json = null;
    private HttpResponse<Buffer> response;

    public WrappedResponse(int code, String body, HttpResponse<Buffer> response) {
      this.code = code;
      this.body = body;
      this.response = response;
      try {
        json = new JsonObject(body);
      } catch(Exception e) {
        json = null;
      }
      System.out.println(String.format("Returning new WrappedResponse with body %s",
              body));
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

  public Future<WrappedResponse> doRequest(Vertx vertx, String url,
          HttpMethod method, MultiMap headers, String payload) {

    log.info(String.format("Sending %s request to endpoint %s with payload %s\n",
            method.toString(), url, payload));

    final Promise<WrappedResponse> promise = Promise.promise();
    final Future<WrappedResponse> future = promise.future();

    WebClient client = WebClient.create(vertx);
    HttpRequest<Buffer> request = client.requestAbs(method, url);

    //Add standard headers
    request.putHeader("X-Okapi-Tenant", "diku")
            .putHeader("content-type", "application/json")
            .putHeader("accept", "application/json");

    if(headers != null) {
      for(Map.Entry<String, String> entry : headers.entries()) {
        request.putHeader(entry.getKey(), entry.getValue());
      }
    }
    
    Future<HttpResponse<Buffer>> response;

    if (payload != null && (method == HttpMethod.PUT || method == HttpMethod.POST)){
      Buffer buffer = Buffer.buffer(payload);
      response = request.sendBuffer(buffer);
    } else {
      response = request.send();
    }

    response.onFailure(err -> {
      log.info(String.format("Request for url %s failed: %s", url, 
        err.getLocalizedMessage()));
      promise.fail(err);
    });

    response.onSuccess(res -> {
      WrappedResponse wr = new WrappedResponse(res.statusCode(), res.bodyAsString(), res);
      log.info(String.format("Got new WrappedResponse object with values code %s, body %s",
        wr.getCode(), wr.getBody()));
      promise.complete(wr);
    });

  return future;
  }
}
