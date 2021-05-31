package org.folio.service.transactions;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.folio.rest.client.CirculationStorageModuleClient;
import org.folio.rest.client.FeesFinesModuleClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.jaxrs.model.OpenTransactions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

public class OpenTransactionsServiceImpl implements OpenTransactionsService {

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
    final var openTransactions = new OpenTransactions().withUserId(id).withUserBarcode(user.getBarcode());

    Future<Integer> loansFuture =
      circulationClient.getOpenLoansCountByUserId(id, connectionParams)
        .onSuccess(openTransactions::setLoans);

    Future<Integer> requestsFuture =
      circulationClient.getOpenRequestsCountByUserId(id, connectionParams)
        .onSuccess(openTransactions::setRequests);

    Future<Integer> accountsFuture =
      feesFinesClient.getOpenAccountsCountByUserId(id, connectionParams)
        .onSuccess(openTransactions::setFeesFines);

    Future<Integer> manualBlockFuture =
      feesFinesClient.getManualBlocksCountByUserId(id, connectionParams)
        .onSuccess(openTransactions::setBlocks);

    Future<Integer> proxiesFuture =
      userClient.getProxiesCountByUserId(id, connectionParams)
        .onSuccess(openTransactions::setProxies);

    return CompositeFuture.all(loansFuture, requestsFuture, accountsFuture, manualBlockFuture, proxiesFuture)
      .map(compositeFuture -> {
        boolean hasOpenTransactions = compositeFuture.result().list().stream()
          .map(Integer.class::cast)
          .anyMatch(n -> n > 0);
        openTransactions.setHasOpenTransactions(hasOpenTransactions);
        return openTransactions;
      });
  }
}
