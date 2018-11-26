package org.folio.rest.client.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.folio.rest.client.NotificationClient;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

public class NotificationClientImpl implements NotificationClient {

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
        if (response.getCode() != HttpStatus.SC_NO_CONTENT) {
          String logMessage =
            String.format("Error sending notification. Status: %d, body: %s", response.getCode(), response.getBody());
          throw new OkapiModuleClientException(logMessage);
        }
        return null;
      });
  }
}
