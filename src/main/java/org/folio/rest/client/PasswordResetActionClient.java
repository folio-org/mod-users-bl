package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;


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
}
