package org.folio.rest.client.impl;

import static java.lang.String.format;
import static org.folio.service.PasswordResetSetting.FOLIO_HOST;
import static org.folio.service.PasswordResetSetting.FORGOT_PASSWORD_UI_PATH;
import static org.folio.service.PasswordResetSetting.RESET_PASSWORD_UI_PATH;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.SettingsClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.service.PasswordResetSetting;

public class SettingsClientImpl implements SettingsClient {

  private static final Logger LOG = LogManager.getLogger(SettingsClientImpl.class);

  private static final String SETTING_VALUE_FIELD = "value";

  private static final String SETTINGS_PATH = "/settings/entries";

  private final HttpClient httpClient;

  public SettingsClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Map<PasswordResetSetting, String>> lookupPasswordResetSettings(OkapiConnectionParams okapiConnectionParams) {
    LOG.info("lookupPasswordResetSettings:: looking for Password Reset Settings");
    var keysQuery = Arrays.stream(PasswordResetSetting.values())
      .map(settingKeyEnum -> format("key==%s", settingKeyEnum.getKey()))
      .collect(Collectors.joining(" or ", "(", ")"));
    var requestUrl = format("%s?query=scope==mod-users-bl and %s", okapiConnectionParams.getOkapiUrl() + SETTINGS_PATH, keysQuery);
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET, okapiConnectionParams.buildHeaders(), null)
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_OK) {
          var logMessage =
            format("Error getting Password Reset Settings. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }

        var responseJson = response.getJson();
        var enabledSettings = validateResponseAndGetEnabledPasswordResetSettings(responseJson);
        return asSettingKeyToValueMap(enabledSettings);
      });
  }

  private List<JsonObject> validateResponseAndGetEnabledPasswordResetSettings(JsonObject responseJson) {
    if (responseJson == null || !responseJson.containsKey("items")) {
      throw new OkapiModuleClientException("Invalid Password Reset Settings response: missing 'items' field");
    }

    var items = responseJson.getJsonArray("items");
    if (items.isEmpty()) {
      throw new OkapiModuleClientException("No Password Reset Settings found");
    }

    var enabledSettings = items.stream()
      .map(JsonObject.class::cast)
      .filter(settingJson -> settingJson.getJsonObject(SETTING_VALUE_FIELD) != null)
      .filter(settingJson -> {
        var valueObj = settingJson.getJsonObject(SETTING_VALUE_FIELD);
        return Boolean.TRUE.equals(valueObj.getBoolean("enabled", false));
      })
      .toList();

    if (enabledSettings.isEmpty()) {
      throw new OkapiModuleClientException("No enabled Password Reset Settings found");
    }

    return enabledSettings;
  }

  private Map<PasswordResetSetting, String> asSettingKeyToValueMap(List<JsonObject> enabledSettings) {
    return enabledSettings.stream()
      .map(settingJson -> {
        var valueObj = settingJson.getJsonObject(SETTING_VALUE_FIELD);
        if (valueObj.containsKey(FOLIO_HOST.getKey())) {
          return Optional.of(Map.entry(FOLIO_HOST, valueObj.getString(FOLIO_HOST.getKey())));
        }

        if (valueObj.containsKey(RESET_PASSWORD_UI_PATH.getKey())) {
          return Optional.of(Map.entry(RESET_PASSWORD_UI_PATH, valueObj.getString(RESET_PASSWORD_UI_PATH.getKey())));
        }

        if (valueObj.containsKey(FORGOT_PASSWORD_UI_PATH.getKey())) {
          return Optional.of(Map.entry(FORGOT_PASSWORD_UI_PATH, valueObj.getString(FORGOT_PASSWORD_UI_PATH.getKey())));
        }

        return Optional.empty();
      })
      .filter(Optional::isPresent)
      .map(op -> ((Optional<Map.Entry<PasswordResetSetting, String>>)op).get())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
