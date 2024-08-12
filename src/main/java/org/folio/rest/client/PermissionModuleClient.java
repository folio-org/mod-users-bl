package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

public interface PermissionModuleClient {
  Future<Boolean> deleteModPermissionByUserId(String userId, OkapiConnectionParams connectionParams);
}
