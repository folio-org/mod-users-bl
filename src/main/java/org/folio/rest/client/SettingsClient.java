package org.folio.rest.client;

import java.util.Map;
import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.service.PasswordResetSetting;

/**
 * Client for mod-settings module
 */
public interface SettingsClient {

  /**
   * Searches for password reset settings. Returns failed future in case found configuration does not contain required one
   *
   * @param okapiConnectionParams connection params
   * @return future with map containing found configuration
   */
  Future<Map<PasswordResetSetting, String>> lookupPasswordResetSettings(OkapiConnectionParams okapiConnectionParams);
}
