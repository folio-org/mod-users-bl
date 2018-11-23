package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.TokenResponse;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Service for password reset link functionality
 */
public interface PasswordResetLinkService {

  /**
   * Retrieves reset password action id from x-okapi-token connection param, retrieves password reset action,
   * validates the action, then signs new system JWT token.
   *
   * @param okapiConnectionParams connection params
   * @return new JWT token
   */
  Future<TokenResponse> validateLinkAndLoginUser(OkapiConnectionParams okapiConnectionParams);
}
