package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.PasswordResetActionClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.Optional;

public class PasswordResetActionClientImpl implements PasswordResetActionClient {

  private static final String PW_RESET_ENDPOINT = "/authn/reset-password";
  private static final String PW_RESET_ACTION_ENDPOINT = "/authn/password-reset-action";

  private HttpClient httpClient;

  public PasswordResetActionClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Boolean> saveAction(PasswordResetAction passwordRestAction, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + PW_RESET_ACTION_ENDPOINT;

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

  @Override
  public Future<Optional<PasswordResetAction>> getAction(String passwordResetActionId, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + PW_RESET_ACTION_ENDPOINT + "/" + passwordResetActionId;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      okapiConnectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(resp -> {
          if (resp.getCode() == HttpStatus.SC_OK) {
            return Optional.of(resp.getJson().mapTo(PasswordResetAction.class));
          } else {
            return Optional.empty();
          }
        }
      );
  }

  @Override
  public Future<Void> resetPassword(String passwordResetActionId, String newPassword, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + PW_RESET_ENDPOINT;
    JsonObject payload = new JsonObject()
      .put("passwordResetActionId", passwordResetActionId)
      .put("newPassword", newPassword);

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST,
      okapiConnectionParams.buildHeaders(), payload.encode())
      .map(resp -> {
        if (resp.getCode() != HttpStatus.SC_CREATED) {
          throw new OkapiModuleClientException();
        }
        return null;
      });
  }
}
