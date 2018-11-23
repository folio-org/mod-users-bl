package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Service for password reset link functionality
 */
public interface PasswordRestLinkService {

  /**
   * Generates password reset link and sends it to user
   * @param userId id of user to reset password
   * @param okapiConnectionParams parameters for requests to okapi modules
   * @return Future with generated link
   */
  Future<String> sendPasswordRestLink(String userId, OkapiConnectionParams okapiConnectionParams);
}
