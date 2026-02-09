package org.folio.rest;

import static org.folio.rest.MockOkapi.getToken;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.StringUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class GeneratePasswordRestLinkTest {

  private static final String TENANT = "diku";
  private static final String GENERATE_PASSWORD_RESET_LINK_PATH = "/bl-users/password-reset/link";
  private static final String NOTIFY_PATH = "/notify";
  private static final String MODULE_NAME = "USERSBL";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String SETTING_ENTRY_KEY = "key";
  private static final String SETTING_ENTRY_VALUE = "value";
  private static final String SETTING_PASSWORD_RESET_KEY_VALUE = "resetPasswordHost";
  private static final String SETTING_PASSWORD_RESET_VALUE = "http://localhost:3000";
  private static final String CREATE_PASSWORD_EVENT_CONFIG_ID = "CREATE_PASSWORD_EVENT";
  private static final String RESET_PASSWORD_EVENT_CONFIG_ID = "RESET_PASSWORD_EVENT";
  private static final String MOCK_FOLIO_UI_HOST = "http://localhost:3000";
  private static final String DEFAULT_UI_URL = "/reset-password";
  private static final String DEFAULT_FORGOT_PASSWORD_URL = "/forgot-password";
  private static final String MOCK_TOKEN = "eyJhbGciOiJIUzM4NCJ9.eyJkdW1teSI6dHJ1ZSwic3ViIjoiVU5ERUZJTkVEX1VTRVJfX1JFU0VUX1BBU1NXT1JEXzRkMjMzOWZmLWI0ZDktNDU4MC1hNTc0LThiYWM3YWI2YjBhZiIsInR5cGUiOiJkdW1teS1leHBpcmluZyIsImV4dHJhX3Blcm1pc3Npb25zIjpbInVzZXJzLWJsLnBhc3N3b3JkLXJlc2V0LWxpbmsudmFsaWRhdGUiLCJ1c2Vycy1ibC5wYXNzd29yZC1yZXNldC1saW5rLnJlc2V0Il0sImV4cCI6MTcyNDY2NjE3MywidGVuYW50IjoiZGlrdSJ9.L7h_vsh47dFo537KeO8MYZV1xSmHJ5E7XR_9uhPYs30orGBACoydq-uJZrtFB-JK";
  private static final String MOCK_USERNAME = "username";
  private static final String PASSWORD_RESET_ACTION_PATH = "/authn/password-reset-action";

  private static Vertx vertx;
  private static int port;
  private static RequestSpecification spec;

  @org.junit.Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  private Header mockUrlHeader;

  @BeforeClass
  public static void setUp(TestContext context) {
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port));
    TestUtil.deploy(RestVerticle.class, options, vertx, context);

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri("http://localhost:" + port)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, getToken(UUID.randomUUID().toString(), "admin", "diku"))
      .build();
  }

  @Before
  public void setUp() {
    mockUrlHeader = new Header(BLUsersAPI.OKAPI_URL_HEADER, "http://localhost:" + mockServer.port());
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationContainResetLinkWithExpirationTimeOfToken() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    String expectedForgotPasswordLink = MOCK_FOLIO_UI_HOST + DEFAULT_FORGOT_PASSWORD_URL;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));

    List<PasswordResetAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordResetAction.class);
    assertThat(requestBodyByUrl, hasSize(1));
    assertThat(requestBodyByUrl.get(0).getUserId(), is(mockUser.getId()));


    Notification expectedNotification = new Notification()
      .withEventConfigName(RESET_PASSWORD_EVENT_CONFIG_ID)
      .withText(StringUtils.EMPTY)
      .withLang("en")
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink)
          .withAdditionalProperty("forgotPasswordLink", expectedForgotPasswordLink))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification))));
  }

  @Test
  public void generateWithLegacyConfigurationAndSendResetPasswordNotificationWhenPasswordExistsWith() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(true);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));

    List<PasswordResetAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordResetAction.class);
    assertThat(requestBodyByUrl, hasSize(1));
    assertThat(requestBodyByUrl.get(0).getUserId(), is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigName(RESET_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void generateAndSendResetPasswordNotificationWhenPasswordExistsWith() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));

    List<PasswordResetAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordResetAction.class);
    assertThat(requestBodyByUrl, hasSize(1));
    assertThat(requestBodyByUrl.get(0).getUserId(), is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigName(RESET_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWithExpirationTimeWhenTokenSignEndPointReturns404AndUsesLegacyToken() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthTokenLegacy(MOCK_TOKEN);
    mockSignAuthTokenNotFound();
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));

    List<PasswordResetAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordResetAction.class);
    assertThat(requestBodyByUrl, hasSize(1));
    assertThat(requestBodyByUrl.get(0).getUserId(), is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigName(RESET_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void cannotGeneratePasswordWhenTokenSignEndPointReturns500() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthTokenNotFound();
    mockSignAuthTokenServerError();
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

  }

  @Test
  public void cannotGeneratePasswordWhenTokenSignLegacyEndPointReturns500() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthTokenNotFound();
    mockSignAuthTokenLegacyServerError();
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

  }

  @Test
  public void shouldNotGenerateAndSendPasswordWhenLegacyTokenEndpointReturns404() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthTokenNotFound();
    mockSignAuthTokenLegacyNotFound();
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldGenerateAndSendCreatePasswordNotificationWhenPasswordNotExists() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = false;

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    String expectedForgotPasswordLink = MOCK_FOLIO_UI_HOST + DEFAULT_FORGOT_PASSWORD_URL;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));

    List<PasswordResetAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordResetAction.class);
    assertThat(requestBodyByUrl, hasSize(1));
    assertThat(requestBodyByUrl.get(0).getUserId(), is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigName(CREATE_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink)
          .withAdditionalProperty("forgotPasswordLink", expectedForgotPasswordLink))
      .withLang("en")
      .withRecipientId(mockUser.getId())
      .withText(StringUtils.EMPTY);
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldReturn422WhenUserNotFound() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);

    mockUserNotFound(mockUser.getId());
    mockPasswordResetConfig(false);

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  public void shouldReturn422WhenUserHasNoUsername() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());

    mockPasswordResetConfig(false);
    mockUserFound(mockUser.getId(), mockUser);

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_CONTENT);
  }

  @Test
  public void shouldGenerateLinkWhenSendNotificationFailed() {
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);

    mockUserFound(mockUser.getId(), mockUser);
    mockPasswordResetConfig(false);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(true);
    mockNotificationModuleWithServerError();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String expectedLink = MOCK_FOLIO_UI_HOST + DEFAULT_UI_URL + '/' + MOCK_TOKEN + "?tenant=" + TENANT;
    RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));
  }

  private String toJson(Object object) {
    return ObjectMapperTool.valueAsString(object);
  }

  private void mockPasswordResetConfig(boolean isLegacyConfig) {
    if (isLegacyConfig) {
      mockConfigModule();
      return;
    }

    mockPasswordResetSettings();
  }

  private void mockConfigModule() {
    Map<String, String> config = new HashMap<>();
    config.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    List<Config> configList = config.entrySet().stream()
      .map(e -> new Config().withCode(e.getKey()).withValue(e.getValue()).withEnabled(true))
      .toList();

    var query = "scope==\"mod-users-bl.config.manage\" AND (key==\"resetPasswordHost\" or key==\"resetPasswordPath\" or key==\"forgotPasswordPath\")";
    String expectedQuery = "/settings/entries?query=" + StringUtil.urlEncode(query);
    var emptySetting = new JsonObject().put("items", new JsonArray());
    WireMock.stubFor(WireMock.get(expectedQuery)
      .willReturn(WireMock.okJson(emptySetting.encode())));

    Configurations configurations = new Configurations().withConfigs(configList).withTotalRecords(configList.size());
    expectedQuery = String.format("/configurations/entries?query=module==%s", MODULE_NAME);
    WireMock.stubFor(WireMock.get(expectedQuery)
      .willReturn(WireMock.okJson(toJson(configurations))));
  }

  private void mockPasswordResetSettings() {
    var resetPasswordSettingMock = new HashMap<>();
    resetPasswordSettingMock.put(SETTING_ENTRY_KEY, SETTING_PASSWORD_RESET_KEY_VALUE);
    resetPasswordSettingMock.put(SETTING_ENTRY_VALUE, SETTING_PASSWORD_RESET_VALUE);
    var setting = JsonObject.mapFrom(resetPasswordSettingMock);
    var settings = new JsonObject().put("items", new JsonArray().add(0, setting));
    var query = "scope==\"mod-users-bl.config.manage\" AND (key==\"resetPasswordHost\" or key==\"resetPasswordPath\" or key==\"forgotPasswordPath\")";
    String expectedQuery = "/settings/entries?query=" + StringUtil.urlEncode(query);
    WireMock.stubFor(WireMock.get(expectedQuery)
      .willReturn(WireMock.okJson(settings.encode())));
  }

  private void mockUserFound(String userId, User response) {
    WireMock.stubFor(WireMock.get("/users/" + userId)
      .willReturn(WireMock.okJson(toJson(response))));
  }

  private void mockUserNotFound(String userId) {
    WireMock.stubFor(WireMock.get("/users/" + userId)
      .willReturn(WireMock.notFound()));
  }

  private void mockPostPasswordResetAction(boolean passwordExists) {
    JsonObject response = new JsonObject().put("passwordExists", passwordExists);
    WireMock.stubFor(WireMock.post(PASSWORD_RESET_ACTION_PATH)
      .willReturn(WireMock.created().withBody(response.encode())));
  }

  private void mockSignAuthTokenNotFound() {
    WireMock.stubFor(WireMock.post("/token/sign")
      .willReturn(WireMock.notFound()));
  }

  private void mockSignAuthToken(String tokenForResponse) {
    JsonObject response = new JsonObject().put("token", tokenForResponse);
    WireMock.stubFor(WireMock.post("/token/sign")
      .willReturn(WireMock.created().withBody(response.encode())));
  }

  private void mockSignAuthTokenLegacy(String tokenForResponse) {
    JsonObject response = new JsonObject().put("token", tokenForResponse);
    WireMock.stubFor(WireMock.post("/token")
      .willReturn(WireMock.created().withBody(response.encode())));
  }

  private void mockSignAuthTokenServerError() {
    WireMock.stubFor(WireMock.post("/token/sign")
      .willReturn(WireMock.serverError()));
  }

  private void mockSignAuthTokenLegacyServerError() {
    WireMock.stubFor(WireMock.post("/token")
      .willReturn(WireMock.serverError()));
  }

  private void mockSignAuthTokenLegacyNotFound() {
    WireMock.stubFor(WireMock.post("/token")
      .willReturn(WireMock.notFound()));
  }

  private void mockNotificationModule() {
    WireMock.stubFor(WireMock.post(NOTIFY_PATH)
      .willReturn(WireMock.created()));
  }

  private void mockNotificationModuleWithServerError() {
    WireMock.stubFor(WireMock.post(NOTIFY_PATH)
      .willReturn(WireMock.serverError()));
  }

  private <T> List<T> getRequestBodyByUrl(String url, Class<T> clazz) {
    return WireMock.getAllServeEvents().stream()
      .filter(request -> request.getStubMapping().getRequest().getUrl().equals(url))
      .map(request -> request.getRequest().getBodyAsString())
      .map(JsonObject::new)
      .map(jsonObject -> jsonObject.mapTo(clazz))
      .toList();
  }

}
