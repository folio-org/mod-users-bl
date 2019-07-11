package org.folio.service.password;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
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

  // Timeout to wait for response
  private int lookupTimeout = Integer
    .parseInt(RestVerticle.MODULE_SPECIFIC_ARGS.getOrDefault("lookup.timeout", "1000"));

  // Http client to call programmatic rules as internal OKAPI endpoints
  private HttpClient httpClient;

  private final Logger logger = LoggerFactory.getLogger(UserPasswordServiceImpl.class);

  private void initHttpClient(final Vertx vertx) {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    this.httpClient = vertx.createHttpClient(options);
  }

  public UserPasswordServiceImpl() {
  }

  public UserPasswordServiceImpl(final Vertx vertx) {
    initHttpClient(vertx);
  }

  @Override
  public UserPasswordService validateNewPassword(String userId, String newPassword, JsonObject okapiConnectionParams,
                                                 Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    try {
      OkapiConnectionParams params = okapiConnectionParams.mapTo(OkapiConnectionParams.class);
      String url = params.getOkapiUrl() + VALIDATE_URL;

      RestUtil.doRequest(httpClient, url, HttpMethod.POST, params.buildHeaders(), buildPasswordEntity(userId, newPassword))
        .setHandler(h -> {
          if (h.failed() || h.result().getCode() != 200) {
            logger.error("Fail during sending request to validate password", h.cause());
            asyncResultHandler.handle(Future.failedFuture(h.cause()));
          } else {
            JsonObject validateResult = h.result().getJson();
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
            asyncResultHandler.handle(Future.succeededFuture(JsonObject.mapFrom(errors)));
          }
        });
    } catch (Exception e) {
      logger.error("Error during validating user's password", e);
      asyncResultHandler.handle(Future.failedFuture(e));
      return this;
    }
    return this;
  }

  private String buildPasswordEntity(String userId, String newPassword) {
    JsonObject passwordEntity = new JsonObject()
      .put("password", newPassword)
      .put("userId", userId);
    return passwordEntity.encode();
  }

  @Override
  public UserPasswordService updateUserCredential(JsonObject newPasswordObject, JsonObject requestHeaders,
                                                  Handler<AsyncResult<Integer>> asyncResultHandler) {
    try {
      String url = requestHeaders.getString(BLUsersAPI.OKAPI_URL_HEADER) + UPDATE_URL;

      CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
      headers.addAll(requestHeaders
        .getMap()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue())));

      RestUtil.doRequest(httpClient, url, HttpMethod.POST, headers, newPasswordObject.encode())
        .setHandler(h -> {
          if (h.failed()) {
            logger.error("Fail during sending request to update user's credentials", h.cause());
            asyncResultHandler.handle(Future.failedFuture(h.cause()));
          } else {
            int code = h.result().getCode();
            if (code == 204 || code == 401) {
              asyncResultHandler.handle(Future.succeededFuture(code));
            } else {
              asyncResultHandler.handle(Future.failedFuture(h.result().getBody()));
            }
          }
        });
    } catch (Exception e) {
      logger.error("Error during updating user's credentials", e);
      asyncResultHandler.handle(Future.failedFuture(e));
      return this;
    }
    return this;
  }
}
