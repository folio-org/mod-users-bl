package org.folio.rest;

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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.Context;
import org.folio.rest.jaxrs.model.Notification;
import org.folio.rest.jaxrs.model.PasswordRestAction;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.utils.NetworkUtils;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class GeneratePasswordRestLinkTest {

  private static final String TENANT = "diku";
  private static final String GENERATE_PASSWORD_RESET_LINK_PATH = "/bl-users/password-reset/link";
  private static final String NOTIFY_PATH = "/notify";

  private static final String MODULE_NAME = "USERSBL";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String LINK_EXPIRATION_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_TIME";
  private static final String CREATE_PASSWORD_EVENT_CONFIG_ID = "CREATE_PASSWORD_EVENT";
  private static final String RESET_PASSWORD_EVENT_CONFIG_ID = "RESET_PASSWORD_EVENT";


  private static final String MOCK_FOLIO_UI_HOST = "http://localhost:3000";
  private static final String DEFAULT_UI_URL = "/reset-password";
  private static final String MOCK_TOKEN = "mockToken";
  public static final String PASSWORD_RESET_ACTION_PATH = "/authn/password-reset-action";


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
      .build();
  }

  @Before
  public void setUp() throws Exception {
    mockUrlHeader = new Header(BLUsersAPI.OKAPI_URL_HEADER, "http://localhost:" + mockServer.port());
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void shouldGenerateAndSendResetPasswordNotificationWhenPasswordExists() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());
    boolean passwordExists = true;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordRestAction(passwordExists);
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
      .body("link", Matchers.is(expectedLink));

    List<PasswordRestAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordRestAction.class);
    Assert.assertThat(requestBodyByUrl, Matchers.hasSize(1));
    Assert.assertThat(requestBodyByUrl.get(0).getUserId(), Matchers.is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigId(RESET_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink))
      .withRecipientId(mockUser.getId());
    WireMock.verify(WireMock.postRequestedFor(WireMock.urlMatching(NOTIFY_PATH))
      .withRequestBody(WireMock.equalToJson(toJson(expectedNotification), true, true)));
  }

  @Test
  public void shouldGenerateAndSendCreatePasswordNotificationWhenPasswordExists() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());
    boolean passwordExists = false;

    mockUserFound(mockUser.getId(), mockUser);
    mockConfigModule(MODULE_NAME, configToMock);
    mockSignAuthToken(MOCK_TOKEN);
    mockPostPasswordRestAction(passwordExists);
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
      .body("link", Matchers.is(expectedLink));


    List<PasswordRestAction> requestBodyByUrl =
      getRequestBodyByUrl(PASSWORD_RESET_ACTION_PATH, PasswordRestAction.class);
    Assert.assertThat(requestBodyByUrl, Matchers.hasSize(1));
    Assert.assertThat(requestBodyByUrl.get(0).getUserId(), Matchers.is(mockUser.getId()));

    Notification expectedNotification = new Notification()
      .withEventConfigId(CREATE_PASSWORD_EVENT_CONFIG_ID)
      .withContext(
        new Context()
          .withAdditionalProperty("user", mockUser)
          .withAdditionalProperty("link", expectedLink))
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
      .withId(UUID.randomUUID().toString());
    boolean passwordExists = false;

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
  public void shouldReturn500WhenRequiredConfigNotFound() {
    Map<String, String> emptyConfigToMock = new HashMap<>();
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());
    boolean passwordExists = false;

    mockConfigModule(MODULE_NAME, emptyConfigToMock);
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
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
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

  private void mockPostPasswordRestAction(boolean passwordExists) {
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
    WireMock.stubFor(WireMock.post("/notify")
      .willReturn(WireMock.noContent()));
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
