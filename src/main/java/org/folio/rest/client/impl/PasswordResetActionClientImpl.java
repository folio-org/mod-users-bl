package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.PasswordResetActionClient;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.Optional;

public class PasswordResetActionClientImpl implements PasswordResetActionClient {

  private static final String PASSWORD_RESET_ACTION_PATH = "/authn/password-reset-action/";

  private HttpClient httpClient;

  public PasswordResetActionClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Optional<PasswordRestAction>> getAction(String passwordResetActionId, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + PASSWORD_RESET_ACTION_PATH + passwordResetActionId;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      okapiConnectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(resp -> {
          if (resp.getCode() == HttpStatus.SC_OK) {
            return Optional.of(resp.getJson().mapTo(PasswordRestAction.class));
          } else {
            return Optional.empty();
          }
        }
      );
  }
}
