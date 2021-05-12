package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.FeesFinesModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Accounts;
import org.folio.rest.jaxrs.model.ManualBlocks;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FeesFinesModuleClientImpl implements FeesFinesModuleClient {

  private HttpClient httpClient;

  public FeesFinesModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Integer> getOpenAccountsCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = URLEncoder.encode("(userId==" + userId + " AND status.name=\"Open\")", StandardCharsets.UTF_8);
    String requestUrl = connectionParams.getOkapiUrl() + "/accounts?limit=0&query=" + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            Accounts accounts = response.getJson().mapTo(Accounts.class);
            return accounts.getTotalRecords().intValue();
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            String logMessage =
              String.format("Error looking up open accounts of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  @Override
  public Future<Integer> getManualBlocksCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String requestUrl = connectionParams.getOkapiUrl() + "/manualblocks?limit=0&query=(userId==" + userId + ")";
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            ManualBlocks blocks = response.getJson().mapTo(ManualBlocks.class);
            return blocks.getTotalRecords().intValue();
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            String logMessage =
              String.format("Error looking up manual blocks of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
