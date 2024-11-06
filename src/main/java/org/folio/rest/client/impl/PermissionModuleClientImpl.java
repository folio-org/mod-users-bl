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
    String indexField = "?indexField=userId";
    String requestUrl = connectionParams.getOkapiUrl() + MOD_PERMISSION_ENDPOINT + FORWARD_SLASH + userId + indexField;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
          logger.info("deleteModPermissionByUserId:: [DELETE_MOD_PERMISSION] Successfully " +
            "deleted the modPermissions with UserId: {}", userId);
          return true;
        } else if (response.getCode() == HttpStatus.SC_NOT_FOUND) {
          logger.error("deleteModPermissionByUserId:: [DELETE_MOD_PERMISSION] " +
            "No permissions found with UserId: {}", userId);
          return false;
        }
        String errorLogMsg = String.format("deleteModPermissionByUserId:: [DELETE_MOD_PERMISSION] Error " +
            "while deleting modPermission for userId: %s. Status: %d, body: %s", userId,
          response.getCode(),
          response.getBody());
        logger.error(errorLogMsg);
        throw new OkapiModuleClientException(errorLogMsg);
      });
  }
}
