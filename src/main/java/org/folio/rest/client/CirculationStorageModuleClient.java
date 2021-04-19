package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Loan;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.List;
import java.util.Optional;

/**
 * Client for mod-circulation-storage
 */
public interface CirculationStorageModuleClient {

    Future<Optional<List<Loan>>> lookupOpenLoansByUserId(String userId, OkapiConnectionParams connectionParams);

    Future<Optional<List<Request>>> lookupOpenRequestsByUserId(String userId, OkapiConnectionParams connectionParams);
}
