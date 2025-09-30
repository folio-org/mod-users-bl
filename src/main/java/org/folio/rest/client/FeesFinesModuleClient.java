package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

public interface FeesFinesModuleClient {

    Future<Integer> getOpenAccountsCountByUserId(String userId, OkapiConnectionParams connectionParams);

    Future<Integer> getNonExpiredManualBlocksCountByUserId(String userId, OkapiConnectionParams connectionParams);
}
