package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.Optional;

public class UserModuleClientImpl implements UserModuleClient {

  private HttpClient httpClient;

  public UserModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Optional<User>> lookupUserById(String userId, OkapiConnectionParams connectionParams) {
    String requestUrl = connectionParams.getOkapiUrl() + "/users/" + userId;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            return Optional.of(response.getJson().mapTo(User.class));
          case HttpStatus.SC_NOT_FOUND:
            return Optional.empty();
          default:
            String logMessage =
              String.format("Error looking up for user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
