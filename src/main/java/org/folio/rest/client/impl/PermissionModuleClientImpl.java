package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.PermissionModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

public class PermissionModuleClientImpl implements PermissionModuleClient {
  public static final String MOD_PERMISSION_ENDPOINT = "/perms/users";
  private HttpClient httpClient;

  public PermissionModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Boolean> deleteModPermissionByUserId(String userId, OkapiConnectionParams connectionParams) {
    return this.getModPermissionIdByUserId(userId, connectionParams)
      .compose(modPermissionId-> {
        String query = StringUtil.urlEncode("/" + StringUtil.cqlEncode(modPermissionId));
        String requestUrl = connectionParams.getOkapiUrl() + MOD_PERMISSION_ENDPOINT + query;
        return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
            connectionParams.buildHeaders(), StringUtils.EMPTY)
          .map(response -> {
            if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
              return true;
            }
            String errorLogMsg = String.format("deleteUserRequestPreferenceByUserId:: [DELETE_MOD_PERMISSION] Error " +
                "while deleting modPermission for userId: %s. Status: %d, body: %s", userId,  response.getCode(),
              response.getBody());
            throw new OkapiModuleClientException(errorLogMsg);
          });
      });

  }


  private Future<String> getModPermissionIdByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("?query=userId==" + StringUtil.cqlEncode(userId));
    String requestUrl = connectionParams.getOkapiUrl() + MOD_PERMISSION_ENDPOINT + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        int totalRecords = response.getJson().getInteger("totalRecords");
        if(totalRecords == 0) {
          String logMessage =
            String.format("getUserRequestPreferenceIdByUserId:: [DELETE_GET_MOD_PERMISSION] Error while fetching " +
              "modPermissions for userId: %s. Status: %d, body: %s", userId, response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        return response.getJson().getJsonArray("permissionUsers").getJsonObject(0).getString("id");
      });
  }
}
