package org.folio.rest.util;

import io.vertx.core.http.CaseInsensitiveHeaders;
import org.folio.rest.impl.BLUsersAPI;

import java.util.Map;

/**
 * Wrapper class for Okapi connection params
 */
public class OkapiConnectionParams {

  private String okapiUrl;
  private String tenantId;
  private String token;

  public OkapiConnectionParams() {
  }

  public OkapiConnectionParams(String okapiUrl, String tenantId, String token) {
    this.okapiUrl = okapiUrl;
    this.tenantId = tenantId;
    this.token = token;
  }

  public OkapiConnectionParams(Map<String, String> okapiHeaders) {
    this.okapiUrl = okapiHeaders.get(BLUsersAPI.OKAPI_URL_HEADER);
    this.tenantId = okapiHeaders.get(BLUsersAPI.OKAPI_TENANT_HEADER);
    this.token = okapiHeaders.get(BLUsersAPI.OKAPI_TOKEN_HEADER);
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

  public CaseInsensitiveHeaders buildHeaders() {
    return (CaseInsensitiveHeaders) new CaseInsensitiveHeaders()
      .add(BLUsersAPI.OKAPI_URL_HEADER, okapiUrl)
      .add(BLUsersAPI.OKAPI_TENANT_HEADER, tenantId)
      .add(BLUsersAPI.OKAPI_TOKEN_HEADER, token);
  }
}
