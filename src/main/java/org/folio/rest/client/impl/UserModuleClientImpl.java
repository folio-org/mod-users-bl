package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.ProxyForCollection;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            throw new OkapiModuleClientException(generateErrorLogMsg(response));
        }
      });
  }

  @Override
  public Future<Optional<User>> lookupUserByUserName(String userName, OkapiConnectionParams connectionParams) {
    String requestUrl = connectionParams.getOkapiUrl() + "/users?query=username==\"" + userName + "\"";
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            return extractUser(response.getJson());
          case HttpStatus.SC_NOT_FOUND:
            return Optional.empty();
          default:
            throw new OkapiModuleClientException(generateErrorLogMsg(response));
        }
      });
  }

  @Override
  public Future<Boolean> deleteUserById(String userId, OkapiConnectionParams connectionParams) {
    String requestUrl = connectionParams.getOkapiUrl() + "/users/" + userId;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
          return true;
        }
        throw new OkapiModuleClientException(generateErrorLogMsg(response));
      });
  }

  private Optional<User> extractUser(JsonObject usersJson) {
    JsonArray responseArray = usersJson.getJsonArray("users");
    if (responseArray.isEmpty()) {
      return Optional.empty();
    }
    if (responseArray.size() != 1) {
      String logMessage =
        String.format("Error looking up for user. Expected 1 but %d users found.", responseArray.size());
      throw new OkapiModuleClientException(logMessage);
    }
    return Optional.of(responseArray.getJsonObject(0).mapTo(User.class));
  }

  @Override
  public Future<Integer> getProxiesCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = URLEncoder.encode("(userId==" + userId + " OR proxyUserId==" + userId + ")", StandardCharsets.UTF_8);
    String requestUrl = connectionParams.getOkapiUrl() + "/proxiesfor?limit=0&query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            ProxyForCollection proxies = response.getJson().mapTo(ProxyForCollection.class);
            return proxies.getTotalRecords();
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            throw new OkapiModuleClientException(generateErrorLogMsg(response));
        }
      });
  }

  private String generateErrorLogMsg(RestUtil.WrappedResponse response) {
    return String.format("Error looking up for user. Status: %d, body: %s", response.getCode(), response.getBody());
  }
}
