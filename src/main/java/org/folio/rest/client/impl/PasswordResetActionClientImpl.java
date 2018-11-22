package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.client.PasswordResetActionClient;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

public class PasswordResetActionClientImpl implements PasswordResetActionClient {

  private static final String PASSWORD_RESET_ACTION_PATH = "/authn/password-reset-action";//NOSONAR

  private HttpClient httpClient;

  public PasswordResetActionClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Boolean> saveAction(PasswordRestAction passwordRestAction, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + PASSWORD_RESET_ACTION_PATH;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST,
      okapiConnectionParams.buildHeaders(), JsonObject.mapFrom(passwordRestAction).encode())
      .map((response -> {
        if (response.getCode() != HttpStatus.SC_CREATED) {
          String logMessage =
            String.format("Error saving password reset action. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        return response.getJson().getBoolean("passwordExists");
      }));
  }
}
