package org.folio.rest.util;

import io.vertx.core.http.CaseInsensitiveHeaders;
import org.folio.rest.impl.BLUsersAPI;

/**
 * Wrapper class for Okapi connection params
 */
public class OkapiConnectionParams {

  private String okapiUrl;
  private String tenantId;
  private String token;
  private String userId;

  public OkapiConnectionParams() {
  }

  public OkapiConnectionParams(String okapiUrl, String tenantId, String token, String userId) {
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
    this.userId=userId;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getToken() {
    return token;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public CaseInsensitiveHeaders buildHeaders() {
    return (CaseInsensitiveHeaders) new CaseInsensitiveHeaders()
      .add(BLUsersAPI.OKAPI_URL_HEADER, okapiUrl)
      .add(BLUsersAPI.OKAPI_TENANT_HEADER, tenantId)
      .add(BLUsersAPI.OKAPI_TOKEN_HEADER, token)
      .add(BLUsersAPI.OKAPI_USER_ID, userId);
  }
}
