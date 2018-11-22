package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.util.OkapiConnectionParams;


public interface PasswordResetActionClient {

  Future<Boolean> saveAction(PasswordRestAction passwordRestAction, OkapiConnectionParams okapiConnectionParams);
}
