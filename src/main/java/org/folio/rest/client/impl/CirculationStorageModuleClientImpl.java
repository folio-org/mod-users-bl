package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Loans;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Requests;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.util.StringUtil;

import java.util.List;
import java.util.Optional;

public class CirculationStorageModuleClientImpl implements CirculationStorageModuleClient {

  private HttpClient httpClient;

  public CirculationStorageModuleClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Optional<List<Loan>>> lookupOpenLoansByUserId(String userId, OkapiConnectionParams connectionParams) {

    String query = StringUtil.urlEncode("userId==" + userId + " AND status.name=Open");
    // query = StringUtil.urlEncode(query).replace("+", "%20");
    String requestUrl = connectionParams.getOkapiUrl() + "/loan-storage/loans?query=" + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            Loans loans = response.getJson().mapTo(Loans.class);
            List<Loan> openLoans = loans.getLoans();
            return Optional.of(openLoans);
          case HttpStatus.SC_NOT_FOUND:
            return Optional.empty();
          default:
            String logMessage =
              String.format("Error looking up for user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }

  @Override
  public Future<Optional<List<Request>>> lookupOpenRequestsByUserId(String userId, OkapiConnectionParams connectionParams) {

    String query = StringUtil.urlEncode("(requesterId==" + userId + " AND status=\"Open\")");
    query = StringUtil.urlEncode(query).replace("+", "%20");
    String requestUrl = connectionParams.getOkapiUrl() + "/request-storage/request?query" + query;

    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET,
      connectionParams.buildHeaders(), StringUtils.EMPTY)
      .map(response -> {
        switch (response.getCode()) {
          case HttpStatus.SC_OK:
            Requests reqs = response.getJson().mapTo(Requests.class);
            List<Request> openReqs = reqs.getRequests();
            return Optional.of(openReqs);
          case HttpStatus.SC_NOT_FOUND:
            return Optional.empty();
          default:
            String logMessage =
              String.format("Error looking up for user. Status: %d, body: %s", response.getCode(), response.getBody());
            throw new OkapiModuleClientException(logMessage);
        }
      });
  }
}
