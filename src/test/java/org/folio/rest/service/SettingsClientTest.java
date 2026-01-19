package org.folio.rest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.hc.core5.http.HttpStatus;
import org.folio.rest.client.impl.SettingsClientImpl;
import org.folio.rest.exception.OkapiModuleClientException;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.service.PasswordResetSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;

@ExtendWith(VertxExtension.class)
class SettingsClientTest {

  @Mock
  private HttpClient httpClient;

  private OkapiConnectionParams okapiConnectionParams;

  private SettingsClientImpl settingsClient;

  @BeforeEach
  void setUp() {
    okapiConnectionParams = new OkapiConnectionParams("http://localhost:9130", "test-tenant", "");
    settingsClient = new SettingsClientImpl(httpClient);
  }

  @Test
  void lookupPasswordResetSettings_shouldReturnSettingsMap_whenValidResponse(VertxTestContext testContext) {
    var responseJson = new JsonObject()
      .put("items", new JsonArray()
        .add(new JsonObject()
          .put("key", "resetPasswordPath")
          .put("value", "/reset-password"))
        .add(new JsonObject()
          .put("key", "forgotPasswordPath")
          .put("value", "/forgot-password"))
        .add(new JsonObject()
          .put("key", "resetPasswordHost")
          .put("value", "https://folio.example.com")));

    var response = mock(RestUtil.WrappedResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_OK);
    when(response.getJson()).thenReturn(responseJson);

    try (MockedStatic<RestUtil> restUtilMock = mockStatic(RestUtil.class)) {
      restUtilMock.when(() -> RestUtil.doRequest(any(), anyString(), any(), any(), any()))
        .thenReturn(Future.succeededFuture(response));

      settingsClient.lookupPasswordResetSettings(okapiConnectionParams)
        .onComplete(testContext.succeeding(map -> testContext.verify(() -> {
          assertNotNull(map);
          assertEquals(3, map.size());
          assertEquals("/reset-password", map.get(PasswordResetSetting.RESET_PASSWORD_UI_PATH));
          assertEquals("/forgot-password", map.get(PasswordResetSetting.FORGOT_PASSWORD_UI_PATH));
          assertEquals("https://folio.example.com", map.get(PasswordResetSetting.FOLIO_HOST));
          testContext.completeNow();
        })));
    }
  }

  @Test
  void lookupPasswordResetSettings_shouldThrowException_whenNonOkStatus(VertxTestContext testContext) {
    var response = mock(RestUtil.WrappedResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
    when(response.getBody()).thenReturn("setting not found");

    try (MockedStatic<RestUtil> restUtilMock = mockStatic(RestUtil.class)) {
      restUtilMock.when(() -> RestUtil.doRequest(any(), anyString(), any(), any(), any()))
        .thenReturn(Future.succeededFuture(response));

      settingsClient.lookupPasswordResetSettings(okapiConnectionParams)
          .onComplete(testContext.failing(ex -> testContext.verify(() -> {
            assertInstanceOf(OkapiModuleClientException.class, ex);
            assertTrue(ex.getMessage().contains("" + response.getCode()));
            assertTrue(ex.getMessage().contains("body: " + response.getBody()));
            testContext.completeNow();
          })));
    }
  }

  @Test
  void lookupPasswordResetSettings_shouldThrowException_whenMissingItemsField(VertxTestContext testContext) {
    var responseJson = new JsonObject();
    var response = mock(RestUtil.WrappedResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_OK);
    when(response.getJson()).thenReturn(responseJson);

    try (MockedStatic<RestUtil> restUtilMock = mockStatic(RestUtil.class)) {
      restUtilMock.when(() -> RestUtil.doRequest(any(), anyString(), any(), any(), any()))
        .thenReturn(Future.succeededFuture(response));

      settingsClient.lookupPasswordResetSettings(okapiConnectionParams)
        .onComplete(testContext.failing(ex -> testContext.verify(() -> {
          assertInstanceOf(OkapiModuleClientException.class, ex);
          assertTrue(ex.getMessage().contains("Invalid Password Reset Settings response: missing 'items' field"));
          testContext.completeNow();
        })));
    }
  }

  @Test
  void lookupPasswordResetSettings_shouldThrowException_whenItemsEmpty(VertxTestContext testContext) {
    var responseJson = new JsonObject().put("items", new JsonArray());
    var response = mock(RestUtil.WrappedResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_OK);
    when(response.getJson()).thenReturn(responseJson);

    try (MockedStatic<RestUtil> restUtilMock = mockStatic(RestUtil.class)) {
      restUtilMock.when(() -> RestUtil.doRequest(any(), anyString(), any(), any(), any()))
        .thenReturn(Future.succeededFuture(response));

      settingsClient.lookupPasswordResetSettings(okapiConnectionParams)
        .onComplete(testContext.failing(ex -> testContext.verify(() -> {
          assertInstanceOf(OkapiModuleClientException.class, ex);
          assertTrue(ex.getMessage().contains("No Password Reset Settings found"));
          testContext.completeNow();
        })));
    }
  }

  @Test
  void lookupPasswordResetSettings_shouldThrowException_whenAllValuesBlank(VertxTestContext testContext) {
    var responseJson = new JsonObject()
      .put("items", new JsonArray()
        .add(new JsonObject()
          .put("key", "reset.password.link.validity.minutes")
          .put("value", ""))
        .add(new JsonObject()
          .put("key", "reset.password.ui.path")
          .put("value", null)));
    var response = mock(RestUtil.WrappedResponse.class);
    when(response.getCode()).thenReturn(HttpStatus.SC_OK);
    when(response.getJson()).thenReturn(responseJson);

    try (MockedStatic<RestUtil> restUtilMock = mockStatic(RestUtil.class)) {
      restUtilMock.when(() -> RestUtil.doRequest(any(), anyString(), any(), any(), any()))
        .thenReturn(Future.succeededFuture(response));

      settingsClient.lookupPasswordResetSettings(okapiConnectionParams)
        .onComplete(testContext.failing(ex -> testContext.verify(() -> {
          assertInstanceOf(OkapiModuleClientException.class, ex);
          assertTrue(ex.getMessage().contains("No valid Password Reset Settings found"));
          testContext.completeNow();
        })));
    }
  }
}
