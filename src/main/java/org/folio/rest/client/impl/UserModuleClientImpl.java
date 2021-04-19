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
import org.folio.rest.jaxrs.model.ProxiesFor;
import org.folio.rest.jaxrs.model.ProxyForCollection;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

  @Override
  public Future<Optional<User>> lookupUserByUserName(String userName, OkapiConnectionParams connectionParams) {
    String requestUrl = connectionParams.getOkapiUrl() + "/users/?query=username==\"" + userName + "\"";
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            // TODO: make this cleaner
            {
              JsonObject responseJson = response.getJson();
              JsonArray responseArray = responseJson.getJsonArray("users");
              if (responseArray.size() != 1) {
                String logMessage =
                  String.format("Error looking up for user. None or multiple users found.");
                throw new OkapiModuleClientException(logMessage);
              } else {
                return Optional.of(responseArray.getJsonObject(0).mapTo(User.class));
              }
            }
          case HttpStatus.SC_NOT_FOUND:
            return Optional.empty();
          default:
            String logMessage =
              String.format("Error looking up for user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  private Future<Optional<User>> lookupUser(String requestUrl, OkapiConnectionParams connectionParams) {
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

  @Override
  public Future<Optional<List<ProxiesFor>>> lookUpProxiesByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = URLEncoder.encode("(userId==" + userId + " OR proxyUserId==" + userId + ")", StandardCharsets.UTF_8);
//    query = StringUtil.urlEncode(query).replace("+", "%20");
    String requestUrl = connectionParams.getOkapiUrl() + "/proxiesfor?query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
            connectionParams.buildHeaders(), StringUtils.EMPTY)
            .map(response -> {
              switch (response.getCode()) {
                case HttpStatus.SC_OK:
                  ProxyForCollection proxies = response.getJson().mapTo(ProxyForCollection.class);
                  return Optional.of(proxies.getProxiesFor());
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
