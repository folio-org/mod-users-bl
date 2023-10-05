package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.AuthTokenClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;


public class AuthTokenClientImpl implements AuthTokenClient {
  private static final Logger log = LogManager.getLogger(AuthTokenClientImpl.class);

  private HttpClient httpClient;

  public AuthTokenClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<String> signToken(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams) {
    log.info("signToken:: called");
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/token/sign";
    String requestPayload = new JsonObject().put("payload", tokenPayload).encode();

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST, okapiConnectionParams.buildHeaders(), requestPayload)
      .compose(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            log.info("signToken:: SC_OK");
            return Future.succeededFuture(response.getResponse().headers().get(BLUsersAPI.OKAPI_TOKEN_HEADER));
          case HttpStatus.SC_CREATED:
            log.info("signToken:: SC_CREATED");
            return Future.succeededFuture(response.getJson().getString("token"));
          case HttpStatus.SC_NOT_FOUND:
            log.info("signToken:: SC_NOT_FOUND");
            return signTokenLegacy(tokenPayload, okapiConnectionParams);
          default:
            log.info("signToken:: ERROR");
            String logMessage =
              String.format("Error when signing token. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  public Future<String> signTokenLegacy(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams) {
    log.info("signTokenLegacy:: called");
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/token";
    String requestPayload = new JsonObject().put("payload", tokenPayload).encode();

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST, okapiConnectionParams.buildHeaders(), requestPayload)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            log.info("signTokenLegacy:: SC_OK ");
            return response.getResponse().headers().get(BLUsersAPI.OKAPI_TOKEN_HEADER);
          case HttpStatus.SC_CREATED:
            log.info("signTokenLegacy:: SC_CREATED");
            return response.getJson().getString("token");
          default:
            log.info("signTokenLegacy:: ERROR");
            String logMessage =
              String.format("Error when signing token. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
