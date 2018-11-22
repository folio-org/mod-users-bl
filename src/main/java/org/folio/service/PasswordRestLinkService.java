package org.folio.service;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

public interface PasswordRestLinkService {

  Future<String> sendPasswordRestLink(String userId, OkapiConnectionParams okapiConnectionParams);
}
