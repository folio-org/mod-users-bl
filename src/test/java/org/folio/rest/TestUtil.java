package org.folio.rest;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
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
    private JsonObject json = null;
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
      System.out.println(String.format("Returning new WrappedResponse with body %s",
              body));
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

  public Future<WrappedResponse> doRequest(Vertx vertx, String url,
          HttpMethod method, MultiMap headers, String payload) {
    System.out.println(String.format("Sending %s request to endpoint %s with payload %s\n",
            method.toString(), url, payload));
    Promise<WrappedResponse> promise = Promise.promise();
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
    request.exceptionHandler(e -> {
      System.out.println(String.format("Request for url %s failed: %s", url,
              e.getLocalizedMessage()));
      promise.fail(e);
    });
    request.handler( req -> {
      //System.out.println("Entering doRequest request handler");
      req.bodyHandler(buf -> {
        //System.out.println("Entering doRequest body handler");
        try {
          //System.out.println(String.format(
          //        "Building a new WrappedResponse object with arguments code: %s, body %s, response %s",
          //        req.statusCode(), buf.toString(), req));
          WrappedResponse wr = new WrappedResponse(req.statusCode(), buf.toString(), req);
          System.out.println(String.format(
                  "Got new WrappedResponse object with values code %s, body %s",
                  wr.getCode(), wr.getBody()));
          if(!promise.future().isComplete() && !promise.future().failed()) {
            //System.out.println("Future is not yet completed");
            if(!promise.tryComplete(wr)) {
              System.out.println("Failed to complete future");
            };
          } else {
            //System.out.println("Future is already completed");
          }
          //System.out.println(String.format(
          //        "WrappedResponse Future for request at url %s is completed", url));
        } catch(Exception e) {
          if(!promise.tryFail(e)) {
            System.out.println(String.format(
                    "Got exception %s, but future already complete: %s",
                    e, e.getLocalizedMessage()));
            e.printStackTrace();
          }
        }
      });
    });
    if(method == HttpMethod.PUT || method == HttpMethod.POST) {
      request.end(payload);
    } else {
      request.end();
    }
    return promise.future();
  }
}


