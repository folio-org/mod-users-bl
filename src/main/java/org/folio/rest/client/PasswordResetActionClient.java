package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Optional;


public interface PasswordResetActionClient {

  Future<Optional<PasswordRestAction>> getAction(String passwordResetActionId, OkapiConnectionParams okapiConnectionParams);
}
