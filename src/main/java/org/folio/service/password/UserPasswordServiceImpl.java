package org.folio.service.password;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserPasswordServiceImpl implements UserPasswordService {

  public static final String USER_PASS_SERVICE_ADDRESS = "user-password-service.queue";
  private static final String VALIDATE_URL = "/password/validate";
  private static final String UPDATE_URL = "/authn/update";

  // Http client to call programmatic rules as internal OKAPI endpoints
  private HttpClient httpClient;

  private static final Logger logger = LogManager.getLogger(UserPasswordServiceImpl.class);

  public UserPasswordServiceImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<JsonObject> validateNewPassword(String userId, String newPassword, JsonObject okapiConnectionParams) {
    try {
      OkapiConnectionParams params = okapiConnectionParams.mapTo(OkapiConnectionParams.class);
      String url = params.getOkapiUrl() + VALIDATE_URL;

      return RestUtil.doRequest(httpClient, url, HttpMethod.POST, params.buildHeaders(), buildPasswordEntity(userId, newPassword))
        .compose(response -> {
          if (response.getCode() != 200) {
            logger.error("Fail during validating new password. Response code: {}, body: {}", response.getCode(), response.getBody());
            return Future.failedFuture("Failed to validate new password. Status: %s, body: %s"
              .formatted(response.getCode(), response.getBody()));
          }
          JsonObject validateResult = response.getJson();
          JsonArray messages = validateResult.getJsonArray("messages");
          Errors errors = new Errors();
          errors.setTotalRecords(0);
          if (messages != null && !messages.isEmpty()) {
            errors.setTotalRecords(messages.size());
            List<Error> errorList = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
              Error error = new Error();
              error.setMessage(messages.getString(i));
              error.setCode(messages.getString(i));
              errorList.add(error);
            }
            errors.setErrors(errorList);
          }
          return Future.succeededFuture(JsonObject.mapFrom(errors));
        });
    } catch (Exception e) {
      logger.error("Error during validating user's password", e);
      return Future.failedFuture(e);
    }
  }

  private String buildPasswordEntity(String userId, String newPassword) {
    JsonObject passwordEntity = new JsonObject()
      .put("password", newPassword)
      .put("userId", userId);
    return passwordEntity.encode();
  }

  @Override
  public Future<Integer> updateUserCredential(JsonObject newPasswordObject, JsonObject requestHeaders) {
    try {
      String url = requestHeaders.getString(BLUsersAPI.OKAPI_URL_HEADER) + UPDATE_URL;
      MultiMap headers = MultiMap.caseInsensitiveMultiMap();
      headers.addAll(requestHeaders
        .getMap()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())));

      return RestUtil.doRequest(httpClient, url, HttpMethod.POST, headers, newPasswordObject.encode())
        .compose(response -> {
          int code = response.getCode();
          if (code == 204 || code == 401) {
            return Future.succeededFuture(code);
          }
          return Future.failedFuture(response.getBody());
        });
    } catch (Exception e) {
      logger.error("Error during updating user's credentials", e);
      return Future.failedFuture(e);
    }
  }
}
