package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Optional;

/**
 * Client for password reset actions API in mod-login module
 */
public interface PasswordResetActionClient {

  /**
   * Saves given action
   *
   * @param passwordResetAction    entry to save
   * @param okapiConnectionParams connection params
   * @return future with true if password already exists
   */
  Future<Boolean> saveAction(PasswordResetAction passwordResetAction, OkapiConnectionParams okapiConnectionParams);

  /**
   * Retrieves password reset action with given id
   *
   * @param passwordResetActionId password reset action id
   * @param okapiConnectionParams connection params
   * @return password reset action
   */
  Future<Optional<PasswordResetAction>> getAction(String passwordResetActionId, OkapiConnectionParams okapiConnectionParams);

  /**
   * Resets password
   *
   * @param passwordResetActionId password reset action id
   * @param newPassword           new password
   * @param okapiConnectionParams connection params
   * @return true if password is
   * @return succeeded future if password is reset, otherwise - failed future
   */
  Future<Boolean> resetPassword(String passwordResetActionId, String newPassword, OkapiConnectionParams okapiConnectionParams);
}
