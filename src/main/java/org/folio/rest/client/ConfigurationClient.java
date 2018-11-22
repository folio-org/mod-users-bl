package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.Map;
import java.util.Set;

public interface ConfigurationClient {

  Future<Map<String, String>> lookupConfigByModuleName(String moduleName, Set<String> requiredConfiguration,
                                                       OkapiConnectionParams okapiConnectionParams);
}
