package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Account;
import org.folio.rest.jaxrs.model.Manualblock;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.List;
import java.util.Optional;

public interface FeesFinesModuleClient {

    Future<Optional<List<Account>>> lookupOpenAccountsByUserId(String userId, OkapiConnectionParams connectionParams);

    Future<Optional<List<Manualblock>>> lookupManualBlocksByUserId(String userId, OkapiConnectionParams connectionParams);
}
