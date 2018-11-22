package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Optional;


public interface UserModuleClient {

  Future<Optional<User>> lookupUserById(String userId, OkapiConnectionParams connectionParams);
}
