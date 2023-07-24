package org.folio.service.consortia;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;

import java.util.Map;

public interface CrossTenantUserServiceImpl {

  /**
   * Searching user in CrossTenant if it isn't CrossTenant will return null
   * @param entity value for search query
   * @param okapiHeaders request headers
   * @param errorKey error code
   * @return User
   */
  Future<User> locateCrossTenantUser(String entity, Map<String, String> okapiHeaders, String errorKey);
}
