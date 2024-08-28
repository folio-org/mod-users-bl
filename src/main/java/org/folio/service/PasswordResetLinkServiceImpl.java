package org.folio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
import org.folio.rest.impl.BLUsersAPI;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


public class PasswordResetLinkServiceImpl implements PasswordResetLinkService {

  private static final String MODULE_NAME = "USERSBL";
  private static final Logger logger = LogManager.getLogger(PasswordResetLinkServiceImpl.class);
  private static final String UNDEFINED_USER_NAME = "UNDEFINED_USER__RESET_PASSWORD_";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String FORGOT_PASSWORD_UI_PATH_CONFIG_KEY = "FORGOT_PASSWORD_UI_PATH";
  private static final String LINK_EXPIRATION_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_TIME";
  private static final String LINK_EXPIRATION_UNIT_OF_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_UNIT_OF_TIME";
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

  private static final int MAXIMUM_EXPIRATION_TIME_IN_WEEKS = 4;
  private static final long MAXIMUM_EXPIRATION_TIME = TimeUnit.DAYS.toMillis(7) * MAXIMUM_EXPIRATION_TIME_IN_WEEKS;

  private ConfigurationClient configurationClient;
  private AuthTokenClient authTokenClient;
  private NotificationClient notificationClient;
  private PasswordResetActionClient passwordResetActionClient;
  private UserModuleClient userModuleClient;
  private UserPasswordService userPasswordService;

  private String resetPasswordUIPathDefault;
  private String forgotPasswordUIPathDefault;

  private Vertx vertx;

  public PasswordResetLinkServiceImpl(ConfigurationClient configurationClient, AuthTokenClient authTokenClient,
                                      NotificationClient notificationClient, PasswordResetActionClient passwordResetActionClient,
                                      UserModuleClient userModuleClient, UserPasswordService userPasswordService, Vertx vertx) {
    this.configurationClient = configurationClient;
    this.authTokenClient = authTokenClient;
    this.notificationClient = notificationClient;
    this.passwordResetActionClient = passwordResetActionClient;
    this.userModuleClient = userModuleClient;
    this.resetPasswordUIPathDefault = System.getProperty("reset-password.ui-path.default", "/reset-password");
    this.forgotPasswordUIPathDefault = System.getProperty("forgot-password.ui-path.default","/forgot-password");
    this.userPasswordService = userPasswordService;
    this.vertx = vertx;
  }

  public Future<String> sendPasswordResetLink(User user, Map<String, String> okapiHeaders) {
    logger.info("sendPasswordResetLink 2");
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    Holder<Map<String, String>> configMapHolder = new Holder<>();
    Holder<String> passwordResetActionIdHolder = new Holder<>();
    Holder<Boolean> passwordExistsHolder = new Holder<>();
    Holder<String> tokenHolder = new Holder<>();
    Holder<String> linkHolder = new Holder<>();

     return configurationClient.lookupConfigByModuleName(MODULE_NAME, GENERATE_LINK_REQUIRED_CONFIGURATION, connectionParams)
      .compose(configurations -> {
        configMapHolder.value = configurations;
        if (StringUtils.isBlank(user.getUsername())) {
          String message = "User without username cannot reset password";
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage("user.absent-username", message);
          return Future.failedFuture(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
        }
       return signToken(connectionParams, passwordResetActionIdHolder);
      })
      .compose(token -> isPasswordExists(user.getId(), token, tokenHolder,  connectionParams, configMapHolder, passwordResetActionIdHolder))
      .compose(passwordExists -> sendNotification(connectionParams, passwordExists, tokenHolder,  configMapHolder, linkHolder, user))
      .map(v -> linkHolder.value);

  }

  @Override
  public Future<String> sendPasswordResetLink(String userId, Map<String, String> okapiHeaders) {
   logger.info("sendPasswordResetLink 1" );
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
    logger.info("sendNotification");
    String linkHost = configMapHolder.value.getOrDefault(FOLIO_HOST_CONFIG_KEY, FOLIO_HOST_DEFAULT);
    String linkPath = configMapHolder.value.getOrDefault(UI_PATH_CONFIG_KEY, resetPasswordUIPathDefault);
    String generatedLink = linkHost + linkPath + '/' + tokenHolder.value + "?tenant=" + connectionParams.getTenantId();
    linkHolder.value = generatedLink;

    String forgotPasswordLinkHost = configMapHolder.value.getOrDefault(FOLIO_HOST_CONFIG_KEY, FOLIO_HOST_DEFAULT);
    String forgotPasswordLinkPath = configMapHolder.value.getOrDefault(FORGOT_PASSWORD_UI_PATH_CONFIG_KEY, forgotPasswordUIPathDefault);
    String forgotPasswordLink = forgotPasswordLinkHost + forgotPasswordLinkPath;

    logger.info("generated link");
    logger.info(linkHolder.value);
    logger.info(forgotPasswordLink);

    String eventConfigName = passwordExists ? RESET_PASSWORD_EVENT_CONFIG_NAME : CREATE_PASSWORD_EVENT_CONFIG_NAME;
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
    logger.info("notification");
    logger.info(notification.getEventConfigName());
    logger.info(notification.getContext().getAdditionalProperties());
    return notificationClient.sendNotification(notification, connectionParams);
  }

  private Future<String> signToken(OkapiConnectionParams connectionParams, Holder<String> passwordResetActionIdHolder) {
    passwordResetActionIdHolder.value = UUID.randomUUID().toString();
//    Boolean passwordExists, Holder<Boolean> passwordExistsHolder,
//    logger.info(passwordExists);
//    passwordExistsHolder.value = passwordExists;
    JsonObject tokenPayload = new JsonObject()
      .put("sub", UNDEFINED_USER_NAME + passwordResetActionIdHolder.value)
      .put("dummy", true)
      .put("extra_permissions", new JsonArray()
        .add("users-bl.password-reset-link.validate")
        .add("users-bl.password-reset-link.reset")
      );
    logger.info("token payload");
    logger.info(tokenPayload);
    return authTokenClient.signToken(tokenPayload, connectionParams);
  }

  public Future<Boolean> isPasswordExists(String userId, String token, Holder<String> tokenHolder, OkapiConnectionParams connectionParams, Holder<Map<String, String>> configMapHolder, Holder<String> passwordResetActionIdHolder) {
    logger.info("isPasswordExists");
    tokenHolder.value = token;

    BLUsersAPI blUsersAPI = new BLUsersAPI(vertx, null);
    JsonObject tokenPayload = blUsersAPI.parseTokenPayload(token);
    Long exp = tokenPayload.getLong("exp");

    System.out.println("exp value" + tokenPayload.getLong("exp") );
    Date expirationDate1 = new Date(exp);
    Date expirationDate2 = new Date(exp * 1000);

    System.out.println("sreeja" + expirationDate1);

    PasswordResetAction actionToCreate = new PasswordResetAction()
      .withId(passwordResetActionIdHolder.value)
      .withUserId(userId)
     .withExpirationTime(expirationDate2);

    logger.info("expirationtime" + actionToCreate.getExpirationTime());
    logger.info("action id" + actionToCreate.getId());
    logger.info("user id" + actionToCreate.getUserId());
    return passwordResetActionClient.saveAction(actionToCreate, connectionParams);
  }

  private long convertDateToMilliseconds(String expirationTimeString, String expirationUnitOfTime) {
    long expirationTime;
    try {
      expirationTime = Long.parseLong(expirationTimeString);
    } catch (NumberFormatException e) {
      String message = "Can't convert time period to milliseconds";
      UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(LINK_INVALID_STATUS_CODE, message);
      throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
    }
    ExpirationTimeUnit timeUnit = ExpirationTimeUnit.of(expirationUnitOfTime);
    if (timeUnit == ExpirationTimeUnit.MINUTES) {
      return TimeUnit.MINUTES.toMillis(expirationTime);
    } else if (timeUnit == ExpirationTimeUnit.HOURS) {
      return TimeUnit.HOURS.toMillis(expirationTime);
    } else if (timeUnit == ExpirationTimeUnit.DAYS) {
      return TimeUnit.DAYS.toMillis(expirationTime);
    } else if (timeUnit == ExpirationTimeUnit.WEEKS) {
      return TimeUnit.DAYS.toMillis(7) * expirationTime;
    }
    String message = "Can't convert time period to milliseconds";
    UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(LINK_INVALID_STATUS_CODE, message);
    throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
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
    logger.info("validateLink");
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
        System.out.println("pwdResetAction.getExpirationTime()" + pwdResetAction.getExpirationTime());
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
        System.out.println("checkPasswordResetActionPresence");
        System.out.println(pwdResetAction.get());
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
