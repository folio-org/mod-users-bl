package org.folio.rest;

import static org.folio.rest.MockOkapi.getToken;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class GeneratePasswordRestLinkTest {

  private static final Logger log = LogManager.getLogger(GeneratePasswordRestLinkTest.class); 

  private static final String TENANT = "diku";
  private static final String GENERATE_PASSWORD_RESET_LINK_PATH = "/bl-users/password-reset/link";
  private static final String NOTIFY_PATH = "/notify";

  private static final String MODULE_NAME = "USERSBL";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String CREATE_PASSWORD_EVENT_CONFIG_ID = "CREATE_PASSWORD_EVENT";
  private static final String RESET_PASSWORD_EVENT_CONFIG_ID = "RESET_PASSWORD_EVENT";
  private static final String EXPIRATION_TIME_MINUTES = "15";
  private static final String EXPIRATION_TIME_HOURS = "24";
  private static final String EXPIRATION_TIME_DAYS = "2";
  private static final String EXPIRATION_TIME_WEEKS = "3";
  private static final String EXPIRATION_UNIT_OF_TIME_HOURS = "hours";
  private static final String EXPIRATION_UNIT_OF_TIME_MINUTES = "minutes";
  private static final String EXPIRATION_UNIT_OF_TIME_DAYS = "days";
  private static final String EXPIRATION_UNIT_OF_TIME_WEEKS = "weeks";
  private static final String EXPIRATION_UNIT_OF_TIME_INCORRECT = "test";
  private static final String RESET_PASSWORD_LINK_EXPIRATION_UNIT_OF_TIME = "RESET_PASSWORD_LINK_EXPIRATION_UNIT_OF_TIME";
  private static final String RESET_PASSWORD_LINK_EXPIRATION_TIME = "RESET_PASSWORD_LINK_EXPIRATION_TIME";
  private static final String MOCK_FOLIO_UI_HOST = "http://localhost:3000";
  private static final String DEFAULT_UI_URL = "/reset-password";
  private static final String MOCK_TOKEN = "mockToken";
  private static final String MOCK_USERNAME = "username";
  private static final String PASSWORD_RESET_ACTION_PATH = "/authn/password-reset-action";
  private static final String EXPIRATION_TIME_WEEKS_MAX = "4";

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
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri("http://localhost:" + port)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, getToken())
      .build();
  }

  @Before
  public void setUp() {
    mockUrlHeader = new Header(BLUsersAPI.OKAPI_URL_HEADER, "http://localhost:" + mockServer.port());
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenPasswordWithDefaultExpirationTime() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

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
          .withAdditionalProperty("link", expectedLink)
          .withAdditionalProperty("expirationTime", EXPIRATION_TIME_HOURS)
          .withAdditionalProperty("expirationUnitOfTime", EXPIRATION_UNIT_OF_TIME_HOURS))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenPasswordWithMinutesOfExpirationTime() {
    generateAndSendResetPasswordNotificationWhenPasswordExistsWith(EXPIRATION_TIME_MINUTES,
      EXPIRATION_UNIT_OF_TIME_MINUTES, EXPIRATION_TIME_MINUTES, EXPIRATION_UNIT_OF_TIME_MINUTES);
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenPasswordWithDaysOfExpirationTime() {
    generateAndSendResetPasswordNotificationWhenPasswordExistsWith(EXPIRATION_TIME_DAYS,
      EXPIRATION_UNIT_OF_TIME_DAYS, EXPIRATION_TIME_DAYS, EXPIRATION_UNIT_OF_TIME_DAYS);
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenPasswordWithWeeksOfExpirationTime() {
    generateAndSendResetPasswordNotificationWhenPasswordExistsWith(EXPIRATION_TIME_WEEKS,
      EXPIRATION_UNIT_OF_TIME_WEEKS, EXPIRATION_TIME_WEEKS, EXPIRATION_UNIT_OF_TIME_WEEKS);
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenExpirationTimeIsBiggerThanMax() {
    generateAndSendResetPasswordNotificationWhenPasswordExistsWith(EXPIRATION_TIME_MINUTES, EXPIRATION_UNIT_OF_TIME_WEEKS,
      EXPIRATION_TIME_WEEKS_MAX, EXPIRATION_UNIT_OF_TIME_WEEKS);
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenExpirationTimeIsIncorrect() {
    shouldHandleExceptionWhenConvertTime("TEST", EXPIRATION_UNIT_OF_TIME_MINUTES);
  }

  @Test
  public void shouldGenerateAndSendPasswordNotificationWhenExpirationOfUnitTimeIsIncorrect() {
    shouldHandleExceptionWhenConvertTime(EXPIRATION_TIME_WEEKS, EXPIRATION_UNIT_OF_TIME_INCORRECT);
  }

  public void shouldHandleExceptionWhenConvertTime(
    String expirationTime, String expirationTimeOfUnit){
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    configToMock.put(RESET_PASSWORD_LINK_EXPIRATION_TIME, expirationTime);
    configToMock.put(RESET_PASSWORD_LINK_EXPIRATION_UNIT_OF_TIME, expirationTimeOfUnit);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

    JsonObject requestBody = new JsonObject()
      .put("userId", mockUser.getId());
    String actual = RestAssured.given()
      .spec(spec)
      .header(mockUrlHeader)
      .body(requestBody.encode())
      .when()
      .post(GENERATE_PASSWORD_RESET_LINK_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .contentType(MediaType.APPLICATION_JSON)
      .extract()
      .asString();

    assertThat(actual, containsString("Can't convert time period to milliseconds"));
  }

  public void generateAndSendResetPasswordNotificationWhenPasswordExistsWith(
    String expirationTime, String expirationTimeOfUnit, String expectedExpirationTime,
    String expectedExpirationTimeOfUnit) {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    configToMock.put(RESET_PASSWORD_LINK_EXPIRATION_TIME, expirationTime);
    configToMock.put(RESET_PASSWORD_LINK_EXPIRATION_UNIT_OF_TIME, expirationTimeOfUnit);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

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
          .withAdditionalProperty("link", expectedLink)
          .withAdditionalProperty("expirationTime", expectedExpirationTime)
          .withAdditionalProperty("expirationUnitOfTime", expectedExpirationTimeOfUnit))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldGenerateAndSendCreatePasswordNotificationWhenPasswordExists() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);
    boolean passwordExists = false;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(passwordExists);
    mockNotificationModule();

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
          .withAdditionalProperty("expirationTime", EXPIRATION_TIME_HOURS)
          .withAdditionalProperty("expirationUnitOfTime", EXPIRATION_UNIT_OF_TIME_HOURS))
      .withLang("en")
      .withRecipientId(mockUser.getId())
      .withText(StringUtils.EMPTY);
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldReturn422WhenUserNotFound() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);

    mockUserNotFound(mockUser.getId());
    mockConfigModule(MODULE_NAME, configToMock);

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
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturn422WhenUserHasNoUsername() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());

    mockConfigModule(MODULE_NAME, configToMock);
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
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldGenerateLinkWhenSendNotificationFailed() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString())
      .withUsername(MOCK_USERNAME);

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordResetAction(true);
    mockNotificationModuleWithServerError();

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
      .statusCode(HttpStatus.SC_OK)
      .body("link", is(expectedLink));
  }

  private String toJson(Object object) {
    return JsonObject.mapFrom(object).toString();
  }


  private void mockConfigModule(String moduleName, Map<String, String> config) {
    List<Config> configList = config.entrySet().stream()
      .map(e -> new Config().withCode(e.getKey()).withValue(e.getValue()).withEnabled(true))
      .collect(Collectors.toList());
    Configurations configurations = new Configurations().withConfigs(configList).withTotalRecords(configList.size());
    String expectedQuery = String.format("/configurations/entries?query=module==%s", moduleName);
    WireMock.stubFor(WireMock.get(expectedQuery)
      .willReturn(WireMock.okJson(toJson(configurations))));
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

  private void mockSignAuthToken(String tokenForResponse) {
    JsonObject response = new JsonObject().put("token", tokenForResponse);
    WireMock.stubFor(WireMock.post("/token")
      .willReturn(WireMock.created().withBody(response.encode())));
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
      .collect(Collectors.toList());
  }

}
