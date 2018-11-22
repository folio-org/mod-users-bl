package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.TokenResponse;
import org.folio.rest.util.OkapiConnectionParams;

public interface PasswordRestLinkService {

  Future<TokenResponse> validateLinkAndLoginUser(OkapiConnectionParams okapiConnectionParams);
}
