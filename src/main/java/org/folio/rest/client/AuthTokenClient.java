package org.folio.rest.client;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Client for mod-authtoken module
 */
public interface AuthTokenClient {

  /**
   * Signs JWT token using given payload
   *
   * @param tokenPayload          token payload
   * @param okapiConnectionParams connection params
   * @return future with generated token
   */
  Future<String> signToken(JsonObject tokenPayload, OkapiConnectionParams okapiConnectionParams);
}
