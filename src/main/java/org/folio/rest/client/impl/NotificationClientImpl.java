package org.folio.rest.client.impl;

import org.apache.http.HttpStatus;
import org.folio.rest.client.NotificationClient;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class NotificationClientImpl implements NotificationClient {

  private static final Logger LOG = LoggerFactory.getLogger("mod-users-bl");
  private HttpClient httpClient;

  public NotificationClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Void> sendNotification(Notification notification, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/notify";
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST,
      okapiConnectionParams.buildHeaders(), JsonObject.mapFrom(notification).encode())
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_CREATED) {
          LOG.error(String.format("Error sending notification. Status: %d, body: %s",
            response.getCode(), response.getBody()));
        }
        return null;
      });
  }
}
