package org.folio.rest.client.impl;


import org.apache.hc.core5.http.HttpStatus;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.client.NotificationClient;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class NotificationClientImpl implements NotificationClient {

  private static final Logger LOG = LogManager.getLogger(NotificationClientImpl.class);
  private HttpClient httpClient;

  public NotificationClientImpl(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public Future<Void> sendNotification(Notification notification, OkapiConnectionParams okapiConnectionParams) {
    String requestUrl = okapiConnectionParams.getOkapiUrl() + "/notify";
    return RestUtil.doRequest(httpClient, requestUrl, HttpMethod.POST,
      okapiConnectionParams.buildHeaders(), ObjectMapperTool.valueAsString(notification))
      .map(response -> {
        if (response.getCode() != HttpStatus.SC_CREATED) {
          LOG.error(String.format("Error sending notification. Status: %d, body: %s",
            response.getCode(), response.getBody()));
        }
        return null;
      });
  }
}
