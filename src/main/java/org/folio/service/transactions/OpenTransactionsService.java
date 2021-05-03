package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.jaxrs.model.UserTransactions;
import org.folio.rest.util.OkapiConnectionParams;

public interface OpenTransactionsService {

  Future<UserTransactions> getTransactionsOfUser(User user, OkapiConnectionParams connectionParams);
}