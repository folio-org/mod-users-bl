package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.service.password.UserPasswordService;

import javax.xml.ws.Holder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PasswordResetLinkServiceImpl implements PasswordResetLinkService {

  private static final Logger LOG = LogManager.getLogger(PasswordResetLinkServiceImpl.class);

  private static final String MODULE_NAME = "USERSBL";
  private static final String UNDEFINED_USER_NAME = "UNDEFINED_USER__RESET_PASSWORD_";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String FORGOT_PASSWORD_UI_PATH_CONFIG_KEY = "FORGOT_PASSWORD_UI_PATH";
  private static final Set<String> GENERATE_LINK_REQUIRED_CONFIGURATION = Collections.emptySet();
  private static final String FOLIO_HOST_DEFAULT = "http://localhost:3000";
  private static final String CREATE_PASSWORD_EVENT_CONFIG_NAME = "CREATE_PASSWORD_EVENT";//NOSONAR
  private static final String RESET_PASSWORD_EVENT_CONFIG_NAME = "RESET_PASSWORD_EVENT";//NOSONAR
  private static final String PASSWORD_CREATED_EVENT_CONFIG_NAME = "PASSWORD_CREATED_EVENT";//NOSONAR
  private static final String PASSWORD_CHANGED_EVENT_CONFIG_NAME = "PASSWORD_CHANGED_EVENT";//NOSONAR
  private static final String DEFAULT_NOTIFICATION_LANG = "en";
  private static final String LINK_INVALID_STATUS_CODE = "link.invalid";
  private static final String LINK_EXPIRED_STATUS_CODE = "link.expired";
  private static final String LINK_USED_STATUS_CODE = "link.used";

  private ConfigurationClient configurationClient;
  private AuthTokenClient authTokenClient;
  private NotificationClient notificationClient;
  private PasswordResetActionClient passwordResetActionClient;
  private UserModuleClient userModuleClient;
  private UserPasswordService userPasswordService;
  private String resetPasswordUIPathDefault;
  private String forgotPasswordUIPathDefault;

  public PasswordResetLinkServiceImpl(ConfigurationClient configurationClient, AuthTokenClient authTokenClient,
                                      NotificationClient notificationClient, PasswordResetActionClient passwordResetActionClient,
                                      UserModuleClient userModuleClient, UserPasswordService userPasswordService) {
    this.configurationClient = configurationClient;
    this.authTokenClient = authTokenClient;
    this.notificationClient = notificationClient;
    this.passwordResetActionClient = passwordResetActionClient;
    this.userModuleClient = userModuleClient;
    this.resetPasswordUIPathDefault = System.getProperty("reset-password.ui-path.default", "/reset-password");
    this.forgotPasswordUIPathDefault = System.getProperty("forgot-password.ui-path.default", "/forgot-password");
    this.userPasswordService = userPasswordService;
  }

  public Future<String> sendPasswordResetLink(User user, Map<String, String> okapiHeaders) {
    LOG.info("sendPasswordResetLink:: user details {}", user);
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    Holder<Map<String, String>> configMapHolder = new Holder<>();
    Holder<String> passwordResetActionIdHolder = new Holder<>();
    Holder<String> tokenHolder = new Holder<>();
    Holder<String> linkHolder = new Holder<>();

     return configurationClient.lookupConfigByModuleName(MODULE_NAME, GENERATE_LINK_REQUIRED_CONFIGURATION, connectionParams)
      .compose(configurations -> {
        configMapHolder.value = configurations;
        if (StringUtils.isBlank(user.getUsername())) {
          LOG.info("sendPasswordResetLink:: Error,User without username cannot reset password");
          String message = "User without username cannot reset password";
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("user.absent-username", message);
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
        }
        return signToken(connectionParams, passwordResetActionIdHolder);
      })
      .compose(token -> isPasswordExists(user.getId(), token, tokenHolder, connectionParams, passwordResetActionIdHolder))
      .compose(passwordExists -> sendNotification(connectionParams, passwordExists, tokenHolder, configMapHolder, linkHolder, user))
      .map(v -> linkHolder.value);
  }

  @Override
  public Future<String> sendPasswordResetLink(String userId, Map<String, String> okapiHeaders) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    return userModuleClient.lookupUserById(userId, connectionParams)
      .compose(optionalUser -> {
        if (optionalUser.isEmpty()) {
          String message = String.format("User with id '%s' not found", userId);
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("user.not-found", message);
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
        }
        return Future.succeededFuture(optionalUser.get());
      })
      .compose(user -> sendPasswordResetLink(user, okapiHeaders));
  }

  private Future<Void> sendNotification(OkapiConnectionParams connectionParams, Boolean passwordExists, Holder<String> tokenHolder, Holder<Map<String, String>> configMapHolder, Holder<String> linkHolder, User user) {
    LOG.info("sendNotification:: passwordExistValue {}, User details {}", passwordExists, user);
    String linkHost = configMapHolder.value.getOrDefault(FOLIO_HOST_CONFIG_KEY, FOLIO_HOST_DEFAULT);
    String linkPath = configMapHolder.value.getOrDefault(UI_PATH_CONFIG_KEY, resetPasswordUIPathDefault);
    String generatedLink = linkHost + linkPath + '/' + tokenHolder.value + "?tenant=" + connectionParams.getTenantId();
    linkHolder.value = generatedLink;

    String forgotPasswordLinkHost = configMapHolder.value.getOrDefault(FOLIO_HOST_CONFIG_KEY, FOLIO_HOST_DEFAULT);
    String forgotPasswordLinkPath = configMapHolder.value.getOrDefault(FORGOT_PASSWORD_UI_PATH_CONFIG_KEY, forgotPasswordUIPathDefault);
    String forgotPasswordLink = forgotPasswordLinkHost + forgotPasswordLinkPath;

    boolean passwordExistsValue = (null != passwordExists) && passwordExists;
    String eventConfigName = passwordExistsValue ? RESET_PASSWORD_EVENT_CONFIG_NAME : CREATE_PASSWORD_EVENT_CONFIG_NAME;
    Notification notification = new Notification()
      .withEventConfigName(eventConfigName)
      .withRecipientId(user.getId())
      .withContext(
        new Context()
          .withAdditionalProperty("user", JsonObject.mapFrom(user))
          .withAdditionalProperty("link", generatedLink)
          .withAdditionalProperty("forgotPasswordLink", forgotPasswordLink))
      .withText(StringUtils.EMPTY)
      .withLang(DEFAULT_NOTIFICATION_LANG);
    return notificationClient.sendNotification(notification, connectionParams);
  }

  private Future<String> signToken(OkapiConnectionParams connectionParams, Holder<String> passwordResetActionIdHolder) {
    LOG.info("signToken:: passwordResetActionIdHolder {}" , passwordResetActionIdHolder.value);
    passwordResetActionIdHolder.value = UUID.randomUUID().toString();
    JsonObject tokenPayload = new JsonObject()
      .put("sub", UNDEFINED_USER_NAME + passwordResetActionIdHolder.value)
      .put("dummy", true)
      .put("extra_permissions", new JsonArray()
        .add("users-bl.password-reset-link.validate")
        .add("users-bl.password-reset-link.reset")
      );
    return authTokenClient.signToken(tokenPayload, connectionParams);
  }

  /**
   * This method will create PasswordResetAction object, In mod-login with the userId present in passwordResetAction,
   * it will fetch the user credentials and check if password exists or not.
   *
   */
  public Future<Boolean> isPasswordExists(String userId, String token, Holder<String> tokenHolder, OkapiConnectionParams connectionParams, Holder<String> passwordResetActionIdHolder) {
    LOG.info("isPasswordExists:: PasswordResetAction details. UserId {}, PasswordResetActionId {}", userId,passwordResetActionIdHolder.value);
    tokenHolder.value = token;
    JsonObject payload = new JsonObject(Buffer.buffer(Base64.getDecoder().decode(token.split("\\.")[1])));
    Long exp = payload.getLong("exp");
    Date expirationDate = new Date(exp * 1000);
    PasswordResetAction actionToCreate = new PasswordResetAction()
      .withId(passwordResetActionIdHolder.value)
      .withUserId(userId)
      .withExpirationTime(expirationDate);

    return passwordResetActionClient.saveAction(actionToCreate, connectionParams);
  }


  @Override
  public Future<Void> resetPassword(String newPassword, Map<String, String> requestHeaders) {
    OkapiConnectionParams okapiConnectionParams = new OkapiConnectionParams(requestHeaders);
    Holder<User> userHolder = new Holder<>();
    Holder<String> userIdHolder = new Holder<>();

    return getPasswordResetActionId(okapiConnectionParams)
      .compose(passwordResetActionId ->
        passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
          .compose(checkPasswordResetActionPresence(passwordResetActionId))
          .compose(checkPasswordResetActionExpirationTime(passwordResetActionId))
          .compose(findUserByPasswordResetActionId(okapiConnectionParams, userIdHolder))
          .compose(validateUser(userHolder, userIdHolder))
          .compose(r -> validatePassword(userHolder.value.getId(), newPassword, okapiConnectionParams))
          .compose(res -> passwordResetActionClient.resetPassword(passwordResetActionId, newPassword, requestHeaders))
          .compose(sendPasswordChangeNotification(okapiConnectionParams, userHolder, userIdHolder))
      );
  }

  @Override
  public Future<Void> validateLink(OkapiConnectionParams okapiConnectionParams) {
    return getPasswordResetActionId(okapiConnectionParams)
      .compose(passwordResetActionId -> passwordResetActionClient.getAction(passwordResetActionId, okapiConnectionParams)
        .compose(checkPasswordResetActionPresence(passwordResetActionId))
        .compose(checkPasswordResetActionExpirationTime(passwordResetActionId))
        .compose(pwdResetAction -> userModuleClient.lookupUserById(pwdResetAction.getUserId(), okapiConnectionParams)
          .compose(validatePasswordResetAction(pwdResetAction)))
      );
  }

  private Function<Optional<User>, Future<User>> validateUser(Holder<User> userHolder, Holder<String> userIdHolder) {
    return optionalUser -> {
      if (optionalUser.isPresent()) {
        User user = optionalUser.get();
        userHolder.value = user;
        return Future.succeededFuture(user);
      }
      String message = String.format("User with id '%s' not found", userIdHolder.value);
      UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(LINK_INVALID_STATUS_CODE, message);
      throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
    };
  }

  private Function<Optional<User>, Future<Void>> validatePasswordResetAction(PasswordResetAction pwdResetAction) {
    return user -> {
      if (user.isPresent()) {
        return Future.succeededFuture(null);
      }

      UnprocessableEntityMessage message = new UnprocessableEntityMessage(LINK_INVALID_STATUS_CODE,
        String.format("User with id = %s in not found", pwdResetAction.getUserId()));
      return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
    };
  }

  private Function<PasswordResetAction, Future<Optional<User>>> findUserByPasswordResetActionId(OkapiConnectionParams okapiConnParams,
                                                                                                Holder<String> userIdHolder) {
    return passwordResetAction -> {
      userIdHolder.value = passwordResetAction.getUserId();
      return userModuleClient.lookupUserById(passwordResetAction.getUserId(), okapiConnParams);
    };
  }

  private Function<Boolean, Future<Void>> sendPasswordChangeNotification(OkapiConnectionParams okapiConnectionParams,
                                                                         Holder<User> userHolder,
                                                                         Holder<String> userIdHolder) {
    return isNewPassword -> {
      Notification notification = createNotification(userHolder, userIdHolder, isNewPassword);
      return notificationClient.sendNotification(notification, okapiConnectionParams);
    };
  }

  private Notification createNotification(Holder<User> userHolder,
                                          Holder<String> userIdHolder,
                                          boolean isNewPassword) {
    Context context = new Context()
      .withAdditionalProperty("user", userHolder.value);

    String eventConfigName;
    if (isNewPassword) {
      eventConfigName = PASSWORD_CREATED_EVENT_CONFIG_NAME;
    } else {
      eventConfigName = PASSWORD_CHANGED_EVENT_CONFIG_NAME;
      context.withAdditionalProperty("detailedDateTime", OffsetDateTime.now().toString());
    }

    return new Notification()
      .withRecipientId(userIdHolder.value)
      .withLang("en")
      .withText(StringUtils.EMPTY)
      .withContext(context)
      .withEventConfigName(eventConfigName);
  }

  private Future<String> getPasswordResetActionId(OkapiConnectionParams okapiConnectionParams) {
    String token = okapiConnectionParams.getToken();
    JsonObject payload = new JsonObject(Buffer.buffer(Base64.getDecoder().decode(token.split("\\.")[1])));
    String tokenSub = payload.getString("sub");
    if (!tokenSub.startsWith(UNDEFINED_USER_NAME)) {
      UnprocessableEntityMessage message = new UnprocessableEntityMessage(LINK_INVALID_STATUS_CODE,
        "Invalid token.");
      return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
    }
    return Future.succeededFuture(tokenSub.substring(UNDEFINED_USER_NAME.length()));
  }

  private Function<PasswordResetAction, Future<PasswordResetAction>> checkPasswordResetActionExpirationTime(
    String passwordResetActionId) {
    return pwdResetAction -> {
      if (pwdResetAction.getExpirationTime().toInstant().isAfter(Instant.now())) {
        return Future.succeededFuture(pwdResetAction);
      } else {
        UnprocessableEntityMessage message = new UnprocessableEntityMessage(LINK_EXPIRED_STATUS_CODE,
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
        UnprocessableEntityMessage message = new UnprocessableEntityMessage(LINK_USED_STATUS_CODE,
          String.format("PasswordResetAction with id = %s is not found", passwordResetActionId));
        return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(message)));
      }
    };
  }

  private Future<Void> validatePassword(String userId, String newPassword, OkapiConnectionParams okapiConnectionParams) {
    Promise<Void> promise = Promise.promise();
    userPasswordService
      .validateNewPassword(userId, newPassword, JsonObject.mapFrom(okapiConnectionParams),
        res -> {
          if (res.succeeded()) {
            JsonObject pwdValidationResult = res.result();
            Errors errors = pwdValidationResult.mapTo(Errors.class);
            if (errors.getTotalRecords() == 0) {
              promise.complete();
            } else {
              Exception exception = new UnprocessableEntityException(errors.getErrors().stream()
                .map(error -> new UnprocessableEntityMessage(error.getCode(), error.getMessage()))
                .collect(Collectors.toList()));
              promise.fail(exception);
            }
          } else {
            promise.fail(res.cause());
          }
        });
    return promise.future();
  }

  private enum ExpirationTimeUnit {
    MINUTES, HOURS, DAYS, WEEKS;

    static ExpirationTimeUnit of(String timeUnit) {
      try {
        return valueOf(timeUnit.toUpperCase());
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }
}
