package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.http.HttpStatus;
import org.folio.rest.client.ConfigurationClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class ConfigurationClientImpl implements ConfigurationClient {

  private HttpClient httpClient;
  private String configRequestPath;

  public ConfigurationClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.configRequestPath = System.getProperty("config.client.path", "/configurations/entries");
  }

  @Override
  public Future<Map<String, String>> lookupConfigByModuleName(String moduleName, Set<String> requiredConfiguration,
                                                              OkapiConnectionParams okapiConnectionParams) {
    String requestUrl =
      String.format("%s?query=module==%s", okapiConnectionParams.getOkapiUrl() + configRequestPath, moduleName);
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.GET, okapiConnectionParams.buildHeaders(), null)
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_OK) {
          String logMessage =
            String.format("Error getting config by module name. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        Configurations configurations = response.getJson().mapTo(Configurations.class);
        if (!validateByRequiredConfiguration(configurations, requiredConfiguration)) {
          String logMessage =
            String.format("Configuration for module %s does not contain all required codes:%s", moduleName, requiredConfiguration);
          throw new OkapiModuleClientException(logMessage);
        }
        return convertConfigsToMap(response.getJson().mapTo(Configurations.class));
      });
  }

  private Map<String, String> convertConfigsToMap(Configurations configurations) {
    return configurations.getConfigs().stream()
      .filter(Config::getEnabled)
      .collect(Collectors.toMap(Config::getCode, Config::getValue));
  }

  private boolean validateByRequiredConfiguration(Configurations configurations, Set<String> requiredConfiguration) {
    return configurations.getConfigs().stream()
      .filter(Config::getEnabled)
      .map(Config::getCode)
      .collect(Collectors.toList())
      .containsAll(requiredConfiguration);
  }
}
