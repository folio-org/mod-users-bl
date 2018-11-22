package org.folio.rest.client;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.util.OkapiConnectionParams;

public interface NotificationClient {

  Future<Void> sendNotification(Notification notification, OkapiConnectionParams okapiConnectionParams);
}
