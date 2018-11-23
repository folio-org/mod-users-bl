package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Optional;


/**
 * Client for mod-users
 */
public interface UserModuleClient {

  /**
   * Searches for user by given id
   *
   * @param userId           user id
   * @param connectionParams connection params
   * @return future with optional user
   */
  Future<Optional<User>> lookupUserById(String userId, OkapiConnectionParams connectionParams);
}
