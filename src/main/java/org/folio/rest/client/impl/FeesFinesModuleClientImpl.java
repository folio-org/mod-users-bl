package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.FeesFinesModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.PercentCodec;
import org.folio.util.StringUtil;

import java.time.LocalDate;

public class FeesFinesModuleClientImpl implements FeesFinesModuleClient {

  private HttpClient httpClient;

  public FeesFinesModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Integer> getOpenAccountsCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("(userId==" + StringUtil.cqlEncode(userId) + " AND status.name=" + StringUtil.cqlEncode("Open") + ")");
    String requestUrl = connectionParams.getOkapiUrl() + "/accounts?limit=0&query=" + query;
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
              String.format("Error looking up open accounts of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  @Override
  public Future<Integer> getManualBlocksCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("(userId==" + StringUtil.cqlEncode(userId) + ")");
    return fetchManualBlockByCQLQuery(connectionParams, query);
  }

  @Override
  public Future<Integer> getNonExpiredManualBlocksCountByUserId(String userId, OkapiConnectionParams connectionParams) {
    String query = StringUtil.urlEncode("(userId==" + StringUtil.cqlEncode(userId) + " AND expirationDate>=" + LocalDate.now() + ")");
    return fetchManualBlockByCQLQuery(connectionParams, PercentCodec.encode(query).toString());
  }

  private Future<Integer> fetchManualBlockByCQLQuery(OkapiConnectionParams connectionParams, String query) {
    String requestUrl = connectionParams.getOkapiUrl() + "/manualblocks?limit=0&query=" + query;
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
              String.format("Error looking up manual blocks of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
