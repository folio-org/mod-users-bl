package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Map;

/**
 * Service for password reset link functionality
 */
public interface PasswordResetLinkService {

  /**
   * Generates password reset link and sends it to user
   * @param userId id of user to reset password
   * @param okapiConnectionParams connection params
   * @return Future with generated link
   */
  Future<String> sendPasswordRestLink(String userId, OkapiConnectionParams okapiConnectionParams);

  /**
   * Retrieves reset password action id from x-okapi-token connection param, retrieves password reset action,
   * validates the action, then signs new system JWT token.
   *
   * @param okapiConnectionParams connection params
   */
  Future<Void> validateLink(OkapiConnectionParams okapiConnectionParams);

  /**
   * Reset user password
   *
   * @param newPassword    a new user password
   * @param requestHeaders request headers
   */
  Future<Void> resetPassword(String newPassword, Map<String, String> requestHeaders);
}
