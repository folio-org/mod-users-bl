package org.folio.rest.client.impl;

import static java.lang.String.format;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.service.PasswordResetSetting;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public class ConfigurationClientImpl implements ConfigurationClient {

  private static final Logger LOG = LogManager.getLogger(ConfigurationClientImpl.class);

  private HttpClient httpClient;
  private String configRequestPath;

  public ConfigurationClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.configRequestPath = System.getProperty("config.client.path", "/configurations/entries");
  }

  @Override
  public Future<Map<PasswordResetSetting, String>> lookupConfigByModuleName(String moduleName,
                                                                            OkapiConnectionParams okapiConnectionParams) {
    LOG.info("lookupPasswordResetSettings:: looking for Password Reset Settings");

    var requestUrl = format("%s?query=module==%s", okapiConnectionParams.getOkapiUrl() + configRequestPath, moduleName);
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET, okapiConnectionParams.buildHeaders(), null)
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_OK) {
          var logMessage =
            format("Error getting config by module name. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        var configurations = response.getJson().mapTo(Configurations.class);
        var hasEnabledConfig = configurations.getConfigs().stream().anyMatch(Config::getEnabled);
        if (!hasEnabledConfig) {
          var logMessage =
            format("Configuration for module %s does not contain any enabled config", moduleName);
          throw new OkapiModuleClientException(logMessage);
        }
        return convertConfigsToMap(configurations);
      });
  }

  private Map<PasswordResetSetting, String> convertConfigsToMap(Configurations configurations) {
    return configurations.getConfigs().stream()
      .filter(Config::getEnabled)
      .map(config ->
        PasswordResetSetting.fromName(config.getCode())
          .map(settingKeyEnum -> Map.entry(settingKeyEnum, config.getValue())))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
