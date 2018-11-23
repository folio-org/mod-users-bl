package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Map;
import java.util.Set;

/**
 * Client for mod-configuration module
 */
public interface ConfigurationClient {

  /**
   * Searches for configuration by module name. Returns failed future in case found configuration does not contain required one
   *
   * @param moduleName            module name
   * @param requiredConfiguration set of required configuration codes
   * @param okapiConnectionParams connection params
   * @return future with map containing found configuration
   */
  Future<Map<String, String>> lookupConfigByModuleName(String moduleName, Set<String> requiredConfiguration,
                                                       OkapiConnectionParams okapiConnectionParams);
}
