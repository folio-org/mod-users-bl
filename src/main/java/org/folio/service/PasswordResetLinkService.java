package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.TokenResponse;
import org.folio.rest.util.OkapiConnectionParams;

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
   * @return new JWT token
   */
  Future<TokenResponse> validateLinkAndLoginUser(OkapiConnectionParams okapiConnectionParams);

  Future<Void> resetPassword(String passwordResetActionId, String newPassword,
                             OkapiConnectionParams okapiConnectionParams);
}
