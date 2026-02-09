package org.folio.service.password;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;


/**
 * Async service for user's new password validation and change
 */
@ProxyGen
public interface UserPasswordService {

  static UserPasswordService create(HttpClient httpClient) {
    return new UserPasswordServiceImpl(httpClient);
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
   * @param userId - user Id
   *
   * @param newPassword - new user's password
   *
   * @param okapiConnectionParams - {@link org.folio.rest.util.OkapiConnectionParams}
   *                              json mapped entity with okapi connections params for endpoint's calls
   */
  Future<JsonObject> validateNewPassword(String userId, String newPassword, JsonObject okapiConnectionParams);

  /**
   * Method call mod-login and update user's credentials
   *
   * @param newPasswordObject     - json object entity with new user's password
   * @param okapiConnectionParams - {@link org.folio.rest.util.OkapiConnectionParams}
   *                              json mapped entity with okapi connections params for endpoint's calls
   */
  Future<Integer> updateUserCredential(JsonObject newPasswordObject, JsonObject okapiConnectionParams);
}
