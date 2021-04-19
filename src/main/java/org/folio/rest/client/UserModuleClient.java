package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ProxiesFor;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.List;
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

  /**
   * Searches for user by given username.
   *
   * @param userName         the user name
   * @param connectionParams connection params
   * @return future with optional user
   */
  Future<Optional<User>> lookupUserByUserName(String userName, OkapiConnectionParams connectionParams);

  Future<Optional<List<ProxiesFor>>> lookUpProxiesByUserId(String userId, OkapiConnectionParams connectionParams);
}
