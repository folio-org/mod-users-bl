package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

import java.util.Objects;
import java.util.Optional;


public class CirculationStorageModuleClientImpl implements CirculationStorageModuleClient {

  private static final Logger logger = LogManager.getLogger(CirculationStorageModuleClientImpl.class);

  public static final String REQUEST_PREFERENCES_ENDPOINT = "/request-preference-storage/request-preference";
  private static final String FORWARD_SLASH = "/";

  private HttpClient httpClient;

  public CirculationStorageModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Integer> getOpenLoansCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId) + " AND status.name=" + StringUtil.cqlEncode("Open"));
    String requestUrl = connectionParams.getOkapiUrl() + "/loan-storage/loans?limit=0&query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            return response.getJson().getInteger("totalRecords");
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            String logMessage =
              String.format("Error looking up loans of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  @Override
  public Future<Integer> getOpenRequestsCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("(requesterId==" + StringUtil.cqlEncode(userId) + " AND status=" + StringUtil.cqlEncode("Open") + ")");
    String requestUrl = connectionParams.getOkapiUrl() + "/request-storage/requests?limit=0&query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            return response.getJson().getInteger("totalRecords");
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            String logMessage =
              String.format("Error looking up open requests of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  @Override
  public Future<Boolean> deleteUserRequestPreferenceByUserId(String userId, OkapiConnectionParams connectionParams) {
    return this.getUserRequestPreferenceIdByUserId(userId, connectionParams)
      .compose(requestPreferencesId -> {
        if (Objects.isNull(requestPreferencesId)) {
          logger.error("deleteUserRequestPreferenceByUserId:: [DELETE_USER_REQUEST_PREFERENCE] " +
              "No requestPreferenceStorage record found with UserId: {} and requestPreferencesId: {}", userId,
            requestPreferencesId);
          return Future.succeededFuture(false);
        }
        String requestUrl =
          connectionParams.getOkapiUrl() + REQUEST_PREFERENCES_ENDPOINT + FORWARD_SLASH + requestPreferencesId;
        return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
            connectionParams.buildHeaders(), StringUtils.EMPTY)
          .map(response -> {
            if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
              logger.info("deleteUserRequestPreferenceByUserId:: [DELETE_USER_REQUEST_PREFERENCE] Successfully " +
                  "deleted the UserRequestPreference with UserId: {}, requestPreferencesId: {}", userId,
                requestPreferencesId);
              return true;
            } else if (response.getCode() == HttpStatus.SC_NOT_FOUND) {
              logger.error("deleteUserRequestPreferenceByUserId:: [DELETE_USER_REQUEST_PREFERENCE] " +
                  "No requestPreferenceStorage found with UserId: {} and requestPreferencesId: {}", userId,
                requestPreferencesId);
              return false;
            }
            String errorLogMsg = String.format("deleteUserRequestPreferenceByUserId:: " +
                "[DELETE_USER_REQUEST_PREFERENCE] Error while deleting  " +
                "UserRequestPreference for userId: %s and requestPreferencesId: %s. Status: %d, body: %s", userId,
              requestPreferencesId,
              response.getCode(),
              response.getBody());
            logger.error(errorLogMsg);
            throw new OkapiModuleClientException(errorLogMsg);
          });
      });

  }

  private Future<String> getUserRequestPreferenceIdByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId));
    String requestUrl = connectionParams.getOkapiUrl() + REQUEST_PREFERENCES_ENDPOINT + "?query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        logger.info("getUserRequestPreferenceIdByUserId:: [DELETE_GET_USER_REQUEST_PREFERENCE] response: code: {}, " +
            "body: {}",
          response.getCode(), response.getBody());
        if (response.getCode() != HttpStatus.SC_OK) {
          String errorLogMsg =
            String.format("getUserRequestPreferenceIdByUserId:: [DELETE_GET_USER_REQUEST_PREFERENCE] Error while " +
              "fetching request preference for userId: %s. " +
              "Status: %d, body: %s", userId, response.getCode(), response.getBody());
          logger.error(errorLogMsg);
          throw new OkapiModuleClientException(errorLogMsg);
        }
        return Optional.ofNullable(response.getJson().getJsonArray("requestPreferences"))
          .filter(arr -> !arr.isEmpty())
          .map(arr -> arr.getJsonObject(0))
          .map(jsonObject -> jsonObject.getString("id")).orElse(null);
      });
  }
}
