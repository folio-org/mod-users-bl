package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.util.OkapiConnectionParams;

/**
 * Client for mod-notify
 */
public interface NotificationClient {

  /**
   * Sends notification
   *
   * @param notification          notification
   * @param okapiConnectionParams connection params
   * @return future
   */
  Future<Void> sendNotification(Notification notification, OkapiConnectionParams okapiConnectionParams);
}
