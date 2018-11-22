package org.folio.service.password;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Async service for user's new password validation and change
 */
@ProxyGen
public interface UserPasswordService {

  static UserPasswordService create(Vertx vertx) {
    return new UserPasswordServiceImpl(vertx);
  }

  /**
   * Creates proxy instance that helps to push message into the message queue
   *
   * @param vertx   vertx instance
   * @param address host address
   * @return ValidationEngineService instance
   */
  static UserPasswordService createProxy(Vertx vertx, String address) {
    return new UserPasswordServiceVertxEBProxy(vertx, address);
  }

  /**
   * Use password validator service for validating new user's password
   *
   * @param updateCredentialsJson - {@link org.folio.rest.jaxrs.model.UpdateCredentialsJson}
   *                              json mapped entity with new user's password
   * @param okapiConnectionParams - {@link org.folio.rest.util.OkapiConnectionParams}
   *                              json mapped entity with okapi connections params for endpoint's calls
   * @param asyncResultHandler    - async result with {@link org.folio.rest.jaxrs.model.Errors} entity
   *                              mapped to json object. Errors object with list of errors codes if password is invalid
   */
  @Fluent
  UserPasswordService validateNewPassword(JsonObject updateCredentialsJson, JsonObject okapiConnectionParams,
                                          Handler<AsyncResult<JsonObject>> asyncResultHandler);

  /**
   * Method call mod-login and update user's credentials
   *
   * @param newPasswordObject     - {@link org.folio.rest.jaxrs.model.UpdateCredentialsJson}
   *                              json mapped entity with new user's password
   * @param okapiConnectionParams - {@link org.folio.rest.util.OkapiConnectionParams}
   *                              json mapped entity with okapi connections params for endpoint's calls
   * @param asyncResultHandler - async result with http status code for update operation
   */
  @Fluent
  UserPasswordService updateUserCredential(JsonObject newPasswordObject, JsonObject okapiConnectionParams,
                                           Handler<AsyncResult<Integer>> asyncResultHandler);
}
