package org.folio.service.consortia;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.User;

import java.util.Map;

public interface CrossTenantUserService {

  /**
   * Firstly search user id and tenant id using /user-tenant endpoint from mod-users central tenant
   * by fields like: username, email, phone number.
   * If found - use tenantId and userId to find user in the tenant where user was created
   * @param entity value for search query
   * @param okapiHeaders request headers
   * @param errorKey error code
   * @return User
   */
  Future<User> findCrossTenantUser(String entity, Map<String, String> okapiHeaders, String errorKey);
}
