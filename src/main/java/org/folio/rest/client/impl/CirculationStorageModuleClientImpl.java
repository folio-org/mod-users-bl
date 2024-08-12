package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

import java.util.Collections;

public class CirculationStorageModuleClientImpl implements CirculationStorageModuleClient {

  public static final String REQUEST_PREFERENCES_ENDPOINT = "/request-preference-storage/request-preference";

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
      .compose(requestPreferencesId-> {
        String query = StringUtil.urlEncode("/" + StringUtil.cqlEncode(requestPreferencesId));
        String requestUrl = connectionParams.getOkapiUrl() + REQUEST_PREFERENCES_ENDPOINT + query;
        return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
            connectionParams.buildHeaders(), StringUtils.EMPTY)
          .map(response -> {
            if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
              return true;
            }
            String errorLogMsg = String.format("deleteUserRequestPreferenceByUserId:: " +
                "[DELETE_USER_REQUEST_PREFERENCE] Error while deleting  " +
                "UserRequestPreference for userId: %s. Status: %d, body: %s", userId,  response.getCode(),
              response.getBody());
            throw new OkapiModuleClientException(errorLogMsg);
          });
      });

  }

  private Future<String> getUserRequestPreferenceIdByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("?query=userId==" + StringUtil.cqlEncode(userId));
    String requestUrl = connectionParams.getOkapiUrl() + REQUEST_PREFERENCES_ENDPOINT + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        int totalRecords = response.getJson().getInteger("totalRecords");
        if(totalRecords == 0) {
          String logMessage =
            String.format("getUserRequestPreferenceIdByUserId:: [DELETE_GET_USER_REQUEST_PREFERENCE] Error while " +
              "fetching request preference for userId: %s. " +
              "Status: %d, body: %s", userId, response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        return response.getJson().getJsonArray("requestPreferences").getJsonObject(0).getString("id");
      });
  }
}
