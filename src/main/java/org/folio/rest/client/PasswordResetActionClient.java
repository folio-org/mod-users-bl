package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Optional;

/**
 * Client for password reset actions API in mod-login module
 */
public interface PasswordResetActionClient {

  /**
   * Saves given action
   *
   * @param passwordRestAction    entry to save
   * @param okapiConnectionParams connection params
   * @return future with true if password already exists
   */
  Future<Boolean> saveAction(PasswordRestAction passwordRestAction, OkapiConnectionParams okapiConnectionParams);

  /**
   * Retrieves an password reset action with given id
   *
   * @param passwordResetActionId password reset action id
   * @param okapiConnectionParams connection params
   * @return
   */
  Future<Optional<PasswordRestAction>> getAction(String passwordResetActionId, OkapiConnectionParams okapiConnectionParams);
}
