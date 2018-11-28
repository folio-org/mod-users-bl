package org.folio.service;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.client.AuthTokenClient;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.client.NotificationClient;
import org.folio.rest.client.PasswordResetActionClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.exception.UnprocessableEntityMessage;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.jaxrs.model.TokenResponse;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.service.password.UserPasswordService;

import javax.xml.ws.Holder;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PasswordResetLinkServiceImpl implements PasswordResetLinkService {

  private static final String MODULE_NAME = "USERSBL";

  private static final String UNDEFINED_USER_NAME = "UNDEFINED_USER__RESET_PASSWORD_";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String LINK_EXPIRATION_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_TIME";
  private static final Set<String> GENERATE_LINK_REQUIRED_CONFIGURATION =
    Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(FOLIO_HOST_CONFIG_KEY)));
 private static final String LINK_EXPIRATION_TIME_DEFAULT = "86400000";

  private static final String CREATE_PASSWORD_EVENT_CONFIG_NAME = "CREATE_PASSWORD_EVENT";//NOSONAR
  private static final String RESET_PASSWORD_EVENT_CONFIG_NAME = "RESET_PASSWORD_EVENT";//NOSONAR
  private static final String PASSWORD_CREATED_EVENT_CONFIG_NAME = "PASSWORD_CREATED_EVENT";//NOSONAR
  private static final String PASSWORD_CHANGED_EVENT_CONFIG_NAME = "PASSWORD_CHANGED_EVENT";//NOSONAR
  private static final String DEFAULT_NOTIFICATION_LANG = "en";

  private ConfigurationClient configurationClient;
  private AuthTokenClient authTokenClient;
  private NotificationClient notificationClient;
  private PasswordResetActionClient passwordResetActionClient;
  private UserModuleClient userModuleClient;
  private UserPasswordService userPasswordService;

  private String resetPasswordUIPathDefault;

  public PasswordResetLinkServiceImpl(ConfigurationClient configurationClient, AuthTokenClient authTokenClient,
                                      NotificationClient notificationClient, PasswordResetActionClient passwordResetActionClient,
                                      UserModuleClient userModuleClient, UserPasswordService userPasswordService) {
    this.configurationClient = configurationClient;
    this.authTokenClient = authTokenClient;
    this.notificationClient = notificationClient;
    this.passwordResetActionClient = passwordResetActionClient;
    this.userModuleClient = userModuleClient;
    this.resetPasswordUIPathDefault = System.getProperty("reset-password.ui-path.default", "/reset-password");
    this.userPasswordService = userPasswordService;
  }

  @Override
  public Future<String> sendPasswordRestLink(String userId, OkapiConnectionParams connectionParams) {
    Holder<Map<String, String>> configMapHolder = new Holder<>();
    Holder<User> userHolder = new Holder<>();
    Holder<String> passwordResetActionIdHolder = new Holder<>();
    Holder<Boolean> passwordExistsHolder = new Holder<>();
    Holder<String> linkHolder = new Holder<>();

    return configurationClient.lookupConfigByModuleName(MODULE_NAME, GENERATE_LINK_REQUIRED_CONFIGURATION, connectionParams)
      .compose(configurations -> {
        configMapHolder.value = configurations;
        return userModuleClient.lookupUserById(userId, connectionParams);
      })
      .compose(optionalUser -> {
        if (!optionalUser.isPresent()) {
          String message = String.format("User with id '%s' not found", userId);
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("user.not-found", message);
          throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
        }
        if (StringUtils.isBlank(optionalUser.get().getUsername())) {
          String message = "User without username cannot reset password";
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("user.absent-username", message);
          throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
        }
        userHolder.value = optionalUser.get();
        long expirationTimeFromConfig = Long.parseLong(
          configMapHolder.value.getOrDefault(LINK_EXPIRATION_TIME_CONFIG_KEY, LINK_EXPIRATION_TIME_DEFAULT));
        passwordResetActionIdHolder.value = UUID.randomUUID().toString();
        PasswordResetAction actionToCreate = new PasswordResetAction()
          .withId(passwordResetActionIdHolder.value)
          .withUserId(userId)
          .withExpirationTime(new Date(
            Instant.now()
              .plusMillis(expirationTimeFromConfig)
              .toEpochMilli()));
        return passwordResetActionClient.saveAction(actionToCreate, connectionParams);
      })
      .compose(passwordExists -> {
        passwordExistsHolder.value = passwordExists;
        JsonObject tokenPayload = new JsonObject()
          .put("sub", UNDEFINED_USER_NAME + passwordResetActionIdHolder.value)
          .put("dummy", true)
          .put("extra_permissions", new JsonArray().add("users-bl.password-reset-link.validate"));
        return authTokenClient.signToken(tokenPayload, connectionParams);
      })
      .compose(token -> {
        String linkHost = configMapHolder.value.get(FOLIO_HOST_CONFIG_KEY);
        String linkPath = configMapHolder.value.getOrDefault(UI_PATH_CONFIG_KEY, resetPasswordUIPathDefault);
        String generatedLink = linkHost + linkPath + '/' + token;
        linkHolder.value = generatedLink;

        String eventConfigName = passwordExistsHolder.value ? RESET_PASSWORD_EVENT_CONFIG_NAME : CREATE_PASSWORD_EVENT_CONFIG_NAME;
        Notification notification = new Notification()
          .withEventConfigName(eventConfigName)
          .withRecipientId(userId)
          .withContext(
            new Context()
              .withAdditionalProperty("user", JsonObject.mapFrom(userHolder.value))
              .withAdditionalProperty("link", generatedLink))
          .withText(StringUtils.EMPTY)
          .withLang(DEFAULT_NOTIFICATION_LANG);
        return notificationClient.sendNotification(notification, connectionParams);
      })
      .map(v -> linkHolder.value);
  }

  @Override
  public Future<Void> resetPassword(String passwordResetActionId, String newPassword, OkapiConnectionParams okapiConnectionParams) {
    Holder<User> userHolder = new Holder<>();
    Holder<String> userIdHolder = new Holder<>();

    Future<PasswordResetAction> passwordResetActionFuture = passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
      .compose(checkPasswordResetActionPresence(passwordResetActionId));

    Future<PasswordResetAction> passwordResetActionExpirationFuture = passwordResetActionFuture
      .compose(checkPasswordResetActionExpirationTime(passwordResetActionId));

    Future<User> userFuture = passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
      .compose(checkPasswordResetActionPresence(passwordResetActionId))
      .compose(passwordResetAction -> {
        userIdHolder.value = passwordResetAction.getUserId();
        return userModuleClient.lookupUserById(passwordResetAction.getUserId(), okapiConnectionParams);
      })
      .compose(optionalUser -> {
        if (optionalUser.isPresent()) {
          User user = optionalUser.get();
          userHolder.value = user;
          return Future.succeededFuture(user);
        }
        String message = String.format("User with id '%s' not found", userIdHolder.value);
        UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("link.invalid", message);
        throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
      });

    return CompositeFuture.all(validatePassword(newPassword, okapiConnectionParams),
      passwordResetActionExpirationFuture, userFuture)
      .compose(res ->
        passwordResetActionClient.resetPassword(passwordResetActionId, newPassword, okapiConnectionParams))
      .compose(isNewPassword -> {
        Notification notification = new Notification();
        notification.setRecipientId(userIdHolder.value);
        notification.setLang("en");
        notification.setText(StringUtils.EMPTY);
        notification.setEventConfigName(isNewPassword ?
          PASSWORD_CREATED_EVENT_CONFIG_NAME : PASSWORD_CHANGED_EVENT_CONFIG_NAME);
        notification.setContext(new Context().withAdditionalProperty("user", userHolder.value));
        return notificationClient.sendNotification(notification, okapiConnectionParams);
      });
  }

  @Override
  public Future<TokenResponse> validateLinkAndLoginUser(OkapiConnectionParams okapiConnectionParams) {
    String token = okapiConnectionParams.getToken();
    JsonObject payload = new JsonObject(Buffer.buffer(Base64.getDecoder().decode(token.split("\\.")[1])));
    String tokenSub = payload.getString("sub");
    if (!tokenSub.startsWith(UNDEFINED_USER_NAME)) {
      UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.invalid",
        "Invalid token.");
      return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
    }
    String passwordResetActionId = tokenSub.substring(UNDEFINED_USER_NAME.length());

    return passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
      .compose(checkPasswordResetActionPresence(passwordResetActionId))
      .compose(checkPasswordResetActionExpirationTime(passwordResetActionId))
      .compose(pwdResetAction -> userModuleClient.lookupUserById(pwdResetAction.getUserId(), okapiConnectionParams)
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

  private Function<PasswordResetAction, Future<PasswordResetAction>> checkPasswordResetActionExpirationTime(
    String passwordResetActionId) {
    return pwdResetAction -> {
      if (pwdResetAction.getExpirationTime().toInstant().isAfter(Instant.now())) {
        return Future.succeededFuture(pwdResetAction);
      } else {
        UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.expired",
          String.format("PasswordResetAction with id = %s is expired", passwordResetActionId));
        return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
      }
    };
  }

  private Function<Optional<PasswordResetAction>, Future<PasswordResetAction>> checkPasswordResetActionPresence(
    String passwordResetActionId) {
    return pwdResetAction -> {
      if (pwdResetAction.isPresent()) {
        return Future.succeededFuture(pwdResetAction.get());
      } else {
        UnprocessableEntityMessage message = new UnprocessableEntityMessage("link.used",
          String.format("PasswordResetAction with id = %s is not found", passwordResetActionId));
        return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
      }
    };
  }

  private Future<Void> validatePassword(String newPassword, OkapiConnectionParams okapiConnectionParams) {
    Future<Void> future = Future.future();
    userPasswordService
      .validateNewPassword(new JsonObject().put("newPassword", newPassword), JsonObject.mapFrom(okapiConnectionParams),
        res -> {
          if (res.succeeded()) {
            JsonObject pwdValidationResult = res.result();
            Errors errors = pwdValidationResult.mapTo(Errors.class);
            if (errors.getTotalRecords() == 0) {
              future.complete();
            } else {
              Exception exception = new UnprocessableEntityException(errors.getErrors().stream()
                .map(error -> new UnprocessableEntityMessage(error.getCode(), error.getMessage()))
                .collect(Collectors.toList()));
              future.fail(exception);
            }
          } else {
            future.fail(res.cause());
          }
        });
    return future;
  }
}
