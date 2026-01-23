package org.folio.rest.client.impl;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
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
  private static final String SETTINGS_ENTRIES_FIELD = "items";
  private static final String SETTINGS_PATH = "/settings/entries";

  public static final String SETTING_SCOPE_FIELD = "mod-users-bl.config.manage";

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
    var requestUrl = format("%s?query=scope==%s and %s", okapiConnectionParams.getOkapiUrl() + SETTINGS_PATH, SETTING_SCOPE_FIELD, keysQuery);
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET, okapiConnectionParams.buildHeaders(), null)
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_OK) {
          var logMessage =
            format("Error getting Password Reset Settings. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }

        var responseJson = response.getJson();
        validatePasswordResetSettings(responseJson);
        return asSettingKeyToValueMap(responseJson);
      });
  }

  private void validatePasswordResetSettings(JsonObject responseJson) {
    if (responseJson == null || !responseJson.containsKey(SETTINGS_ENTRIES_FIELD)) {
      throw new OkapiModuleClientException("Invalid Password Reset Settings response: missing 'items' field");
    }

    var items = responseJson.getJsonArray(SETTINGS_ENTRIES_FIELD);
    if (items.isEmpty()) {
      throw new OkapiModuleClientException("No Password Reset Settings found");
    }

    var settings = items.stream()
      .map(JsonObject.class::cast)
      .filter(setting -> StringUtils.isNotBlank(setting.getString(SETTING_VALUE_FIELD)))
      .toList();

    if (settings.isEmpty()) {
      throw new OkapiModuleClientException("No valid Password Reset Settings found");
    }
  }

  private Map<PasswordResetSetting, String> asSettingKeyToValueMap(JsonObject responseJson) {
    return responseJson.getJsonArray(SETTINGS_ENTRIES_FIELD).stream()
      .map(JsonObject.class::cast)
      .collect(Collectors.toMap(s -> PasswordResetSetting.fromValue(s.getString("key")), s -> s.getString(SETTING_VALUE_FIELD)));
  }
}
