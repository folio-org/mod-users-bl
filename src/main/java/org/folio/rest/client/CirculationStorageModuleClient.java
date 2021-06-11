package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Client for mod-circulation-storage
 */
public interface CirculationStorageModuleClient {

    Future<Integer> getOpenLoansCountByUserId(String userId, OkapiConnectionParams connectionParams);

    Future<Integer> getOpenRequestsCountByUserId(String userId, OkapiConnectionParams connectionParams);
}
