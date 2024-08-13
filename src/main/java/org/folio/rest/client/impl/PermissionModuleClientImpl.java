package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.PermissionModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

public class PermissionModuleClientImpl implements PermissionModuleClient {
  private static final Logger logger = LogManager.getLogger(PermissionModuleClientImpl.class);
  public static final String MOD_PERMISSION_ENDPOINT = "/perms/users";
  private static final String FORWARD_SLASH = "/";
  private HttpClient httpClient;

  public PermissionModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Boolean> deleteModPermissionByUserId(String userId, OkapiConnectionParams connectionParams) {
    return this.getModPermissionIdByUserId(userId, connectionParams)
      .compose(modPermissionId-> {
        String requestUrl = connectionParams.getOkapiUrl() + MOD_PERMISSION_ENDPOINT + FORWARD_SLASH + modPermissionId;
        return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
            connectionParams.buildHeaders(), StringUtils.EMPTY)
          .map(response -> {
            if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
              logger.info("deleteModPermissionByUserId:: [DELETE_MOD_PERMISSION] Successfully " +
                "deleted the modPermissions with UserId: {} and modPermissionId: {}", userId, modPermissionId);
              return true;
            }
            String errorLogMsg = String.format("deleteModPermissionByUserId:: [DELETE_MOD_PERMISSION] Error " +
                "while deleting modPermission for userId: %s and modPermissionId: %s. Status: %d, body: %s", userId,
              modPermissionId,
              response.getCode(),
              response.getBody());
            logger.error(errorLogMsg);
            throw new OkapiModuleClientException(errorLogMsg);
          });
      });

  }


  private Future<String> getModPermissionIdByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId));
    String requestUrl = connectionParams.getOkapiUrl() + MOD_PERMISSION_ENDPOINT + "?query=" + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        logger.info("getModPermissionIdByUserId:: response: code: {}, body: {}", response.getCode(), response.getBody());
        int totalRecords = response.getJson().getInteger("totalRecords");
        if(response.getCode()!=HttpStatus.SC_OK || totalRecords == 0) {
          String logMessage =
            String.format("getModPermissionIdByUserId:: [DELETE_GET_MOD_PERMISSION] Error while fetching " +
              "modPermissions for userId: %s. Status: %d, body: %s", userId, response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        return response.getJson().getJsonArray("permissionUsers").getJsonObject(0).getString("id");
      });
  }
}
