package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.AuthTokenClient;
import org.folio.rest.exception.TokenNotFoundException;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;


public class AuthTokenClientImpl implements AuthTokenClient {

  private HttpClient httpClient;

  public AuthTokenClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<String> signToken(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/token/sign";
    String requestPayload = new JsonObject().put("payload", tokenPayload).encode();

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST, okapiConnectionParams.buildHeaders(), requestPayload)
      .compose(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_CREATED:
            return Future.succeededFuture(response.getJson().getString("token"));
          case HttpStatus.SC_NOT_FOUND:
            return signTokenLegacy(tokenPayload, okapiConnectionParams);
          default:
            String logMessage =
              String.format("Error when signing token. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  public Future<String> signTokenLegacy(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/token";
    String requestPayload = new JsonObject().put("payload", tokenPayload).encode();

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST, okapiConnectionParams.buildHeaders(), requestPayload)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_CREATED:
            return response.getJson().getString("token");
          case HttpStatus.SC_NOT_FOUND:
            String notFoundLogMessage =
                    String.format("Token not found. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new TokenNotFoundException(notFoundLogMessage);
          default:
            String logMessage =
              String.format("Error when signing token. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
