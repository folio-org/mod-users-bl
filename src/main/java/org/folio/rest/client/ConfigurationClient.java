package org.folio.rest.client;

import java.util.Map;
import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.service.PasswordResetSetting;

/**
 * Client for mod-configuration module
 */
public interface ConfigurationClient {

  /**
   * Searches for configuration by module name. Returns failed future in case found configuration does not contain required one
   *
   * @param moduleName            module name
   * @param okapiConnectionParams connection params
   * @return future containing found configuration code to value map
   */
  Future<Map<PasswordResetSetting, String>> lookupConfigByModuleName(String moduleName, OkapiConnectionParams okapiConnectionParams);
}
