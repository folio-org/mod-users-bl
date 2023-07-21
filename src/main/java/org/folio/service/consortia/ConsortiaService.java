package org.folio.service.consortia;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;

import java.util.Map;

public interface ConsortiaService {

  Future<User> locateConsortiaUser(String entity, Map<String, String> okapiHeaders, String errorKey);
}
