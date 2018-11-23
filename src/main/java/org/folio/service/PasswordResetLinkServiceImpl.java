package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.folio.rest.client.AuthTokenClient;
import org.folio.rest.client.PasswordResetActionClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.exception.UnprocessableEntityMessage;
import org.folio.rest.jaxrs.model.TokenResponse;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;

public class PasswordResetLinkServiceImpl implements PasswordResetLinkService {

  private AuthTokenClient authTokenClient;
  private PasswordResetActionClient passwordResetActionClient;
  private UserModuleClient userModuleClient;

  public PasswordResetLinkServiceImpl(AuthTokenClient authTokenClient,
                                      PasswordResetActionClient passwordResetActionClient,
                                      UserModuleClient userModuleClient) {
    this.authTokenClient = authTokenClient;
    this.passwordResetActionClient = passwordResetActionClient;
    this.userModuleClient = userModuleClient;
  }

  @Override
  public Future<TokenResponse> validateLinkAndLoginUser(OkapiConnectionParams okapiConnectionParams) {
    String token = okapiConnectionParams.getToken();
    JsonObject payload = new JsonObject(Buffer.buffer(Base64.getDecoder().decode(token.split("\\.")[1])));
    String passwordResetActionId = payload.getString("passwordResetActionId");

    return passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
      .compose(pwdResetAction -> {
        if (pwdResetAction.isPresent()) {
          return Future.succeededFuture(pwdResetAction.get());
        } else {
          UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.used",
            String.format("PasswordResetAction with id = %s is not found", passwordResetActionId));
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
        }
      }).compose(pwdResetAction -> {
        if (pwdResetAction.getExpirationTime().toInstant().isAfter(Instant.now())) {
          return Future.succeededFuture(pwdResetAction);
        } else {
          UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.expired",
            String.format("PasswordResetAction with id = %s is expired", passwordResetActionId));
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
        }
      }).compose(pwdResetAction -> userModuleClient.lookupUserById(pwdResetAction.getUserId(), okapiConnectionParams)
        .map(user -> {
        if (user.isPresent()) {
          return Future.succeededFuture(user.get());
        } else {
          UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.invalid",
            String.format("User with id = %s in not found", pwdResetAction.getUserId()));
          return Future.<User>failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
        }
      })).compose(user -> {
        JsonObject tokenPayload = new JsonObject()
          .put("sub", user.result().getUsername())
          .put("user_id", user.result().getId());
        return authTokenClient.signToken(tokenPayload, okapiConnectionParams).map(newToken -> {
          TokenResponse response = new TokenResponse();
          response.setResetPasswordActionId(passwordResetActionId);
          response.setToken(newToken);
          return response;
        });
      });
  }
}
