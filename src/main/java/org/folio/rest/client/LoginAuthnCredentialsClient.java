package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

public interface LoginAuthnCredentialsClient {
  Future<Boolean> deleteAuthnCredentialsByUserId(String userId, OkapiConnectionParams connectionParams);
}
