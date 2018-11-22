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
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.utils.NetworkUtils;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
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

  private static final String MODULE_NAME = "USERSBL";
  private static final String FOLIO_HOST_CONFIG_KEY = "FOLIO_HOST";
  private static final String UI_PATH_CONFIG_KEY = "RESET_PASSWORD_UI_PATH";
  private static final String LINK_EXPIRATION_TIME_CONFIG_KEY = "RESET_PASSWORD_LINK_EXPIRATION_TIME";


  private static final String MOCK_FOLIO_UI_HOST = "http://localhost:3000";
  private static final String DEFAULT_UI_URL = "/reset-password";
  private static final String MOCK_TOKEN = "mockToken";


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
  public void shouldGenerateAndSendLinkWhenAllModulesReturnExpectedResults() {
    Map<String, String> configToMock = new HashMap<>();
    configToMock.put(FOLIO_HOST_CONFIG_KEY, MOCK_FOLIO_UI_HOST);
    User mockUser = new User()
      .withId(UUID.randomUUID().toString());
    boolean passwordExists = true;

    mockUserModule(mockUser.getId(), mockUser);
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
      .body("link", Matchers.is(expectedLink));

  }

  private String toJson(Object object) {
    return JsonObject.mapFrom(object).toString();
  }

  private void mockConfigModule(String moduleName, Map<String, String> config) {
    List<Config> configList = config.entrySet().stream()
      .map(e -> new Config().withCode(e.getKey()).withValue(e.getValue()).withEnabled(true))
      .collect(Collectors.toList());
    Configurations configurations = new Configurations().withConfigs(configList).withTotalRecords(configList.size());
    String expectedQueryParamValue = String.format("module==%s", moduleName);
    WireMock.stubFor(WireMock.get("/configurations/entries")
      .withQueryParam("query", WireMock.equalTo(expectedQueryParamValue))
      .willReturn(WireMock.okJson(toJson(configurations))));
  }

  private void mockUserModule(String userId, User response) {
    WireMock.stubFor(WireMock.get("/users/" + userId)
      .willReturn(WireMock.okJson(toJson(response))));
  }

  private void mockPostPasswordRestAction(boolean passwordExists) {
    JsonObject response = new JsonObject().put("passwordExists", passwordExists);
    WireMock.stubFor(WireMock.post("/authn/password-reset-action")
      .willReturn(WireMock.okJson(response.encode())));
  }

  private void mockSignAuthToken(String tokenForResponse) {
    JsonObject response = new JsonObject().put("token", tokenForResponse);
    WireMock.stubFor(WireMock.post("/token")
      .willReturn(WireMock.okJson(response.encode())));
  }

  private void mockNotificationModule() {
    WireMock.stubFor(WireMock.post("/notify")
      .willReturn(WireMock.noContent()));
  }

}
