package org.folio.service;

import io.vertx.core.Future;
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
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.util.OkapiConnectionParams;

import javax.xml.ws.Holder;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PasswordResetLinkServiceImpl implements PasswordResetLinkService {

  private static final String MODULE_NAME = "USERSBL";

  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String LINK_EXPIRATION_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_TIME";
  private static final Set<String> GENERATE_LINK_REQUIRED_CONFIGURATION =
    Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(FOLIO_HOST_CONFIG_KEY)));

  private static final String LINK_EXPIRATION_TIME_DEFAULT = "86400000";

  private static final String CREATE_PASSWORD_EVENT_CONFIG_ID = "CREATE_PASSWORD_EVENT";//NOSONAR
  private static final String RESET_PASSWORD_EVENT_CONFIG_ID = "RESET_PASSWORD_EVENT";//NOSONAR
  public static final String DEFAULT_NOTIFICATION_LANG = "en";

  private ConfigurationClient configurationClient;
  private AuthTokenClient authTokenClient;
  private NotificationClient notificationClient;
  private PasswordResetActionClient passwordResetActionClient;
  private UserModuleClient userModuleClient;

  private String resetPasswordUIPathDefault;

  public PasswordResetLinkServiceImpl(ConfigurationClient configurationClient, AuthTokenClient authTokenClient,
                                      NotificationClient notificationClient, PasswordResetActionClient passwordResetActionClient,
                                      UserModuleClient userModuleClient) {
    this.configurationClient = configurationClient;
    this.authTokenClient = authTokenClient;
    this.notificationClient = notificationClient;
    this.passwordResetActionClient = passwordResetActionClient;
    this.userModuleClient = userModuleClient;
    this.resetPasswordUIPathDefault = System.getProperty("reset-password.ui-path.default", "/reset-password");
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
        userHolder.value = optionalUser.get();
        long expirationTimeFromConfig = Long.parseLong(
          configMapHolder.value.getOrDefault(LINK_EXPIRATION_TIME_CONFIG_KEY, LINK_EXPIRATION_TIME_DEFAULT));
        passwordResetActionIdHolder.value = UUID.randomUUID().toString();
        PasswordRestAction actionToCreate = new PasswordRestAction()
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
          .put("sub", passwordResetActionIdHolder.value)
          .put("dummy", true)
          .put("extra_permissions", new JsonArray().add("users-bl.password-reset-link.validate"));
        return authTokenClient.signToken(tokenPayload, connectionParams);
      })
      .compose(token -> {
        String linkHost = configMapHolder.value.get(FOLIO_HOST_CONFIG_KEY);
        String linkPath = configMapHolder.value.getOrDefault(UI_PATH_CONFIG_KEY, resetPasswordUIPathDefault);
        String generatedLink = linkHost + linkPath + '/' + token;
        linkHolder.value = generatedLink;

        String eventConfigId = passwordExistsHolder.value ? RESET_PASSWORD_EVENT_CONFIG_ID : CREATE_PASSWORD_EVENT_CONFIG_ID;
        Notification notification = new Notification()
          .withEventConfigId(eventConfigId)
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
}
