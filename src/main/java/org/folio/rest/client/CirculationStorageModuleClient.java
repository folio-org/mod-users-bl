package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Client for mod-circulation-storage
 */
public interface CirculationStorageModuleClient {

    Future<Integer> getOpenLoansCountByUserId(String userId, OkapiConnectionParams connectionParams);

    Future<Integer> getOpenRequestsCountByUserId(String userId, OkapiConnectionParams connectionParams);

  /**
   * Delete user's request-preference by userId
   * @param userId for which we want to delete request-preference
   * @param connectionParams okapi metadata
   * @return Returns <b>true</b> if the record has been deleted, <br/> otherwise <b>false</b> if no record for the
   * userId exists,
   * <br/> <b>OkapiModuleClientException</b> exception if any exception occurred
   */
    Future<Boolean> deleteUserRequestPreferenceByUserId(String userId, OkapiConnectionParams connectionParams);
}
