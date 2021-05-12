package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

public class CirculationStorageModuleClientImpl implements CirculationStorageModuleClient {

  private HttpClient httpClient;

  public CirculationStorageModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Integer> getOpenLoansCountByUserId(String userId, OkapiConnectionParams connectionParams) {

    String query = StringUtil.urlEncode("userId==" + userId + " AND status.name=Open");
    String requestUrl = connectionParams.getOkapiUrl() + "/loan-storage/loans?limit=0&query=" + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            Loans loans = response.getJson().mapTo(Loans.class);
            return loans.getTotalRecords().intValue();
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

    String query = StringUtil.urlEncode("(requesterId==" + userId + " AND status=\"Open\")");
    query = StringUtil.urlEncode(query).replace("+", "%20");
    String requestUrl = connectionParams.getOkapiUrl() + "/request-storage/requests?limit=0&query" + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            Requests reqs = response.getJson().mapTo(Requests.class);
            return reqs.getTotalRecords().intValue();
          case HttpStatus.SC_NOT_FOUND:
            return 0;
          default:
            String logMessage =
              String.format("Error looking up open requests of user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
