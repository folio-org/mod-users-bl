package org.folio.service.transactions;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.client.FeesFinesModuleClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.jaxrs.model.OpenTransactions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

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
  public Future<OpenTransactions> getTransactionsOfUser(User user, OkapiConnectionParams connectionParams) {
    String id = user.getId();
    Future<Map.Entry<String, Integer>> loansFuture =
      circulationClient.getOpenLoansCountByUserId(id, connectionParams)
        .map(numberLoans -> Map.entry(LOANS, numberLoans));

    Future<Map.Entry<String, Integer>> requestsFuture =
      circulationClient.getOpenRequestsCountByUserId(id, connectionParams)
        .map(numberReqs -> Map.entry(REQUESTS, numberReqs));

    Future<Map.Entry<String, Integer>> accountsFuture =
      feesFinesClient.getOpenAccountsCountByUserId(id, connectionParams)
        .map(accounts -> Map.entry(ACCOUNTS, accounts));

    Future<Map.Entry<String, Integer>> manualBlockFuture =
      feesFinesClient.getManualBlocksCountByUserId(id, connectionParams)
        .map(manualblocks -> Map.entry(MANUAL_BLOCKS, manualblocks));

    Future<Map.Entry<String, Integer>> proxiesFuture =
      userClient.getProxiesCountByUserId(id, connectionParams)
        .map(p -> Map.entry(PROXIES, p));

    Promise<OpenTransactions> result = Promise.promise();
    CompositeFuture.all(loansFuture, requestsFuture, accountsFuture, manualBlockFuture, proxiesFuture)
      .onSuccess(asyncResult -> {
        CompositeFuture compFutResult = asyncResult.result();
        Map<String, Integer> userTransactions = compFutResult.list().stream()
          .map(o -> (Map.Entry<String, Integer>) o)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        boolean hasOpenTransactions = compFutResult.list().stream()
          .map(o -> (Map.Entry<String, Integer>) o)
          .anyMatch(entry -> entry.getValue() > 0);
        OpenTransactions openTransactions = new OpenTransactions()
          .withLoans(userTransactions.get(LOANS))
          .withRequests(userTransactions.get(REQUESTS))
          .withBlocks(userTransactions.get(MANUAL_BLOCKS))
          .withFeesFines(userTransactions.get(ACCOUNTS))
          .withProxies(userTransactions.get(PROXIES))
          .withHasOpenTransactions(hasOpenTransactions)
          .withUserId(id);
        if (user.getBarcode() != null) {
          openTransactions.setUserBarcode(user.getBarcode());
        }
        result.complete(openTransactions);
      })
      .onFailure(result::fail);
    return result.future();
  }
}
