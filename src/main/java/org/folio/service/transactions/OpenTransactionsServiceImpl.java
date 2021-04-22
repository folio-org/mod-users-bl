package org.folio.service.transactions;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.client.FeesFinesModuleClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.model.UserTransactions;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenTransactionsServiceImpl implements OpenTransactionsService {

  public static final String LOANS = "loans";
  public static final String REQUESTS = "requests";
  public static final String ACCOUNTS = "accounts";
  public static final String MANUAL_BLOCKS = "manualblocks";
  public static final String PROXIES = "proxies";
  private CirculationStorageModuleClient circulationClient;
  private FeesFinesModuleClient feesFinesClient;
  private UserModuleClient userClient;

  public OpenTransactionsServiceImpl(CirculationStorageModuleClient circulationClient, FeesFinesModuleClient feesFinesClient, UserModuleClient userClient) {
    this.circulationClient = circulationClient;
    this.feesFinesClient = feesFinesClient;
    this.userClient = userClient;
  }

  @Override
  public Future<UserTransactions> getTransactionsOfUser(User user, OkapiConnectionParams connectionParams) {
    String id = user.getId();
    Future<Map.Entry<String, Integer>> loansFuture =
      circulationClient.lookupOpenLoansByUserId(id, connectionParams)
        .map(loans -> loans.orElse(Collections.emptyList()))
        .map(loans -> Map.entry(LOANS, loans.size()));

    Future<Map.Entry<String, Integer>> requestsFuture =
      circulationClient.lookupOpenRequestsByUserId(id, connectionParams)
        .map(requests -> requests.orElse(Collections.emptyList()))
        .map(requests -> Map.entry(REQUESTS, requests.size()));

    Future<Map.Entry<String, Integer>> accountsFuture =
      feesFinesClient.lookupOpenAccountsByUserId(id, connectionParams)
        .map(accounts -> accounts.orElse(Collections.emptyList()))
        .map(accounts -> Map.entry(ACCOUNTS, accounts.size()));

    Future<Map.Entry<String, Integer>> manualBlockFuture =
      feesFinesClient.lookupManualBlocksByUserId(id, connectionParams)
        .map(manualblocks -> manualblocks.orElse(Collections.emptyList()))
        .map(manualblocks -> Map.entry(MANUAL_BLOCKS, manualblocks.size()));

    Future<Map.Entry<String, Integer>> proxiesFuture =
      userClient.lookUpProxiesByUserId(id, connectionParams)
        .map(p -> p.orElse(Collections.emptyList()))
        .map(p -> Map.entry(PROXIES, p.size()));

    Promise<UserTransactions> result = Promise.promise();
    CompositeFuture.all(loansFuture, requestsFuture, accountsFuture, manualBlockFuture, proxiesFuture)
      .onSuccess(asyncResult -> {
        CompositeFuture compFutResult = asyncResult.result();
        Map<String, Integer> openTransactions = compFutResult.list().stream()
          .map(o -> (Map.Entry<String, Integer>) o)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        boolean hasOpenTransactions = compFutResult.list().stream()
          .map(o -> (Map.Entry<String, Integer>) o)
          .anyMatch(entry -> entry.getValue() > 0);
        UserTransactions userTransactions = new UserTransactions()
          .withLoans(openTransactions.get(LOANS))
          .withRequests(openTransactions.get(REQUESTS))
          .withBlocks(openTransactions.get(MANUAL_BLOCKS))
          .withFeesFines(openTransactions.get(ACCOUNTS))
          .withProxies(openTransactions.get(PROXIES))
          .withHasOpenTransactions(hasOpenTransactions)
          .withUserId(id);
        if (user.getBarcode() != null) {
          userTransactions.setUserBarcode(user.getBarcode());
        }
        result.complete(userTransactions);
      })
      .onFailure(result::fail);
    return result.future();
  }
}
