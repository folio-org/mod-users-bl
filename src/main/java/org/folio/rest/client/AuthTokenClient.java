package org.folio.rest.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.util.OkapiConnectionParams;


public interface AuthTokenClient {

  Future<String> signToken(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams);
}
