package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.OpenTransactions;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

public interface OpenTransactionsService {

  Future<OpenTransactions> getTransactionsOfUser(User user, OkapiConnectionParams connectionParams);
}
