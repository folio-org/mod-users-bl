package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.LoginAuthnCredentialsClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

public class LoginAuthnCredentialsClientImpl implements LoginAuthnCredentialsClient {
  public static final String AUTHN_CREDENTIALS_ENDPOINT = "/authn/credentials";
  private HttpClient httpClient;

  public LoginAuthnCredentialsClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Boolean> deleteAuthnCredentialsByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("?query=userId==" + StringUtil.cqlEncode(userId));
    String requestUrl = connectionParams.getOkapiUrl() + AUTHN_CREDENTIALS_ENDPOINT + query;
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.DELETE,
        connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
          return true;
        }
        String errorLogMsg = String.format("deleteAuthnCredentialsByUserId: [DELETE_AUTHN_CREDENTIAL] Error while " +
            "deleting authnCredentials for userId: %s. Status: %d, body: %s", userId,  response.getCode(),
          response.getBody());
        throw new OkapiModuleClientException(errorLogMsg);
      });
  }
}
