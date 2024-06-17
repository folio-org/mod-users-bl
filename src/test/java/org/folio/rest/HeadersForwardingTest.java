package org.folio.rest;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.restassured.matcher.RestAssuredMatchers;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.LoginCredentials;
import org.folio.rest.jaxrs.model.PasswordReset;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.jaxrs.model.UpdateCredentials;
import org.folio.rest.jaxrs.model.User;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static javax.ws.rs.core.HttpHeaders.USER_AGENT;
import static junit.framework.TestCase.assertTrue;
import static org.folio.rest.MockOkapi.getToken;
import static org.folio.rest.MockOkapi.getTokenWithoutTenant;
import static org.folio.rest.impl.BLUsersAPI.OKAPI_TOKEN_HEADER;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;

@RunWith(VertxUnitRunner.class)
public class HeadersForwardingTest {

  private static final String TENANT = "test";
  private static final String TOKEN = "access_token";
  private static final String USERNAME = "maxi";
  private static final String USER_PASSWORD = "Newpwd!10";
  private static final String USER_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String RESET_PASSWORD_ACTION_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String IP = "216.3.128.12";

  private static final String UNDEFINED_USER_NAME = "UNDEFINED_USER__RESET_PASSWORD_";
  private static final String JWT_TOKEN_PATTERN = "%s.%s.%s";

  private static final String URL_AUT_RESET_PASSWORD = "/authn/reset-password";
  private static final String URL_AUTH_UPDATE = "/authn/update";
  private static final String URL_AUTH_LOGIN_LEGACY = "/authn/login";
  private static final String URL_AUTH_LOGIN = "/authn/login-with-expiry";
  private static final String BL_USERS_LOGIN_LEGACY = "/bl-users/login";
  private static final String BL_USERS_LOGIN = "/bl-users/login-with-expiry";
  private static final String REFRESH_TOKEN = "folioRefreshToken";
  private static final String ACCESS_TOKEN = "folioAccessToken";
  private static final String REFRESH_TOKEN_EXPIRATION = "refreshTokenExpiration";
  private static final String ACCESS_TOKEN_EXPIRATION = "accessTokenExpiration";
  private static final String TOKEN_EXPIRATION = "tokenExpiration";

  private RequestSpecification spec;

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort());

  @Before
  public void setUp(TestContext context) {
    Vertx vertx = Vertx.vertx();
    int port = NetworkUtils.nextFreePort();

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri("http://localhost:" + port)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT)
      .addHeader(RestVerticle.OKAPI_HEADER_TOKEN, TOKEN)
      .addHeader(BLUsersAPI.OKAPI_URL_HEADER, "http://localhost:" + mockServer.port())
      .build();

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port));
    TestUtil.deploy(RestVerticle.class, options, vertx, context);
  }

  @Test
  public void testPostBlUsersLoginLegacy() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    JsonObject user = new JsonObject()
      .put("username", USERNAME)
      .put("id", USER_ID);

    JsonObject users = new JsonObject()
      .put("users", new JsonArray().add(user))
      .put("totalRecords", 1);

    WireMock.stubFor(get(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\""))
      .willReturn(WireMock.okJson(users.encode())));

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(201).withHeader(OKAPI_TOKEN_HEADER, getToken(USER_ID, USERNAME, TENANT))));

    JsonObject permsUsersPost = new JsonObject()
      .put("permissionUsers", new JsonArray().add(new JsonObject()));

    WireMock.stubFor(get(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .willReturn(WireMock.okJson(permsUsersPost.encode()).withStatus(201)));

    JsonObject jsonObject = new JsonObject()
      .put("servicePointsUsers", new JsonArray());

    WireMock.stubFor(get(urlPathEqualTo("/service-points-users"))
        .withQueryParam("query", equalTo("userId==" + USER_ID))
        .withQueryParam("limit", equalTo("1000"))
        .willReturn(WireMock.okJson(jsonObject.encode()).withStatus(201)));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(201);

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\"")));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID)));

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/service-points-users"))
        .withQueryParam("query", equalTo("userId==" + USER_ID))
        .withQueryParam("limit", equalTo("1000")));

    WireMock.getAllServeEvents().stream()
      .map(ServeEvent::getRequest)
      .forEach(this::verifyHeaders);
  }

  @Test
  public void testPostBlUsersLogin() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    JsonObject user = new JsonObject()
      .put("username", USERNAME)
      .put("id", USER_ID);

    JsonObject users = new JsonObject()
      .put("users", new JsonArray().add(user))
      .put("totalRecords", 1);

    JsonObject tokenExpiration = new JsonObject()
      .put("refreshTokenExpiration","987")
      .put("accessTokenExpiration", "456");

    WireMock.stubFor(get(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\""))
      .willReturn(WireMock.okJson(users.encode())));

    var accessToken = getToken(USER_ID, USERNAME, TENANT);
    var accessTokenCookie = Cookie.cookie(ACCESS_TOKEN, accessToken)
      .setMaxAge(321)
      .setSecure(true)
      .setHttpOnly(true);
    var refreshTokenCookie = Cookie.cookie(REFRESH_TOKEN, "abc123")
      .setMaxAge(123)
      .setSecure(true)
      .setPath("/authn")
      .setHttpOnly(true);
    WireMock.stubFor(post(URL_AUTH_LOGIN)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode())
      .withStatus(201)
      .withHeader("Set-Cookie", accessTokenCookie.encode())
      .withHeader("Set-Cookie", refreshTokenCookie.encode())
      .withBody(tokenExpiration.encode())));

    JsonObject permsUsersPost = new JsonObject()
      .put("permissionUsers", new JsonArray().add(new JsonObject().put("id", USER_ID)));

    WireMock.stubFor(get(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .willReturn(WireMock.okJson(permsUsersPost.encode()).withStatus(201)));

    JsonObject permsNames = new JsonObject()
        .put("permissionNames", new JsonArray().add("read").add("write"));

    WireMock.stubFor(get(urlPathTemplate("/perms/users/{userid}/permissions"))
        .willReturn(WireMock.okJson(permsNames.encode()).withStatus(201)));

    JsonObject jsonObject = new JsonObject()
      .put("servicePointsUsers", new JsonArray());

    WireMock.stubFor(get(urlPathEqualTo("/service-points-users"))
        .withQueryParam("query", equalTo("userId==" + USER_ID))
        .withQueryParam("limit", equalTo("1000"))
        .willReturn(WireMock.okJson(jsonObject.encode()).withStatus(201)));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN + "?expandPermissions=true")
      .then()
      .statusCode(201)
      .cookie(REFRESH_TOKEN, RestAssuredMatchers.detailedCookie()
          .value("abc123")
          .maxAge(123)
          .path("/authn")
          .httpOnly(true)
          .secured(true))
      .cookie(ACCESS_TOKEN, RestAssuredMatchers.detailedCookie()
          .value(accessToken)
          .maxAge(321)
          .httpOnly(true)
          .secured(true))
      .body(TOKEN_EXPIRATION, hasKey(ACCESS_TOKEN_EXPIRATION))
      .body(TOKEN_EXPIRATION, hasKey(REFRESH_TOKEN_EXPIRATION))
      .body("permissions.permissions", contains("read", "write"));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\"")));

    WireMock.verify(2, getRequestedFor(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID)));

    WireMock.verify(1, getRequestedFor(urlPathTemplate("/perms/users/{userid}/permissions")));

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN)));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/service-points-users"))
        .withQueryParam("query", equalTo("userId==" + USER_ID))
        .withQueryParam("limit", equalTo("1000")));

    WireMock.getAllServeEvents().stream()
      .map(ServeEvent::getRequest)
      .forEach(this::verifyHeaders);
  }

  @Test
  public void testPostBlUsersLoginWithNullResponseFromLogin() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(null));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(500);

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));
  }

  @Test
  public void testPostBlUsersLoginWithoutTenant() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(201).withHeader(OKAPI_TOKEN_HEADER, getTokenWithoutTenant(USER_ID, USERNAME))));

    WireMock.stubFor(get(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .willReturn(WireMock.notFound()));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(500);

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));
  }

  @Test
  public void testPostBlUsersLoginWithNullToken() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(201).withHeader(OKAPI_TOKEN_HEADER, "")));

    WireMock.stubFor(get(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .willReturn(WireMock.notFound()));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(500);

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));
  }

  @Test
  public void testPostBlUsersLoginWithError() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(422)));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(422);

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));
    WireMock.verify(0, postRequestedFor(urlPathEqualTo("/perms/users")));
  }

  @Test
  public void testPostBlUsersLoginNullResponse() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    WireMock.stubFor(post(URL_AUTH_LOGIN_LEGACY)
      .willReturn(null));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(BL_USERS_LOGIN_LEGACY)
      .then()
      .statusCode(500);

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN_LEGACY)));
    WireMock.verify(0, postRequestedFor(urlPathEqualTo("/perms/users")));
  }

  @Test
  public void testPostBlUsersLoginWithoutUsername() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(null);
    credentials.setPassword("password");

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post("/bl-users/login")
      .then()
      .statusCode(400);

    WireMock.verify(0, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN)));
    WireMock.verify(0, postRequestedFor(urlPathEqualTo("/perms/users")));
  }

  @Test
  public void testPostBlUsersLoginWithoutPassword() {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword(null);

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post("/bl-users/login")
      .then()
      .statusCode(400);

    WireMock.verify(0, postRequestedFor(urlPathEqualTo(URL_AUTH_LOGIN)));
    WireMock.verify(0, postRequestedFor(urlPathEqualTo("/perms/users")));
  }

  @Test
  public void testPostBlUsersLoginIncorrectPermissions() {
    doTestPostBlUsersLoginIncorrectPermissions(URL_AUTH_LOGIN, BL_USERS_LOGIN);
  }

  @Test
  public void testPostBlUsersLoginIncorrectPermissionsLegacy() {
    doTestPostBlUsersLoginIncorrectPermissions(URL_AUTH_LOGIN_LEGACY, BL_USERS_LOGIN_LEGACY);
  }

  private void doTestPostBlUsersLoginIncorrectPermissions(String authLoginEndpoint, String blUsersLoginEndpoint) {
    LoginCredentials credentials = new LoginCredentials();
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");

    JsonObject user = new JsonObject()
      .put("username", USERNAME)
      .put("id", USER_ID);

    JsonObject users = new JsonObject()
      .put("users", new JsonArray().add(user))
      .put("totalRecords", 1);

    WireMock.stubFor(get(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\""))
      .willReturn(WireMock.okJson(users.encode())));

    WireMock.stubFor(post(authLoginEndpoint)
      .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(201).withHeader(OKAPI_TOKEN_HEADER, getToken(USER_ID, USERNAME, TENANT))));

    JsonObject permsUsersPost = new JsonObject()
      .put("permissionUsers", new JsonArray()
        .add(new JsonObject().put("permissions", "INCORRECT")))
      .put("totalRecords", 0);

    WireMock.stubFor(get(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .willReturn(WireMock.okJson(permsUsersPost.encode()).withStatus(201)));

    JsonObject jsonObject = new JsonObject()
      .put("servicePointsUsers", new JsonArray());

    WireMock.stubFor(get(urlPathEqualTo("/service-points-users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .withQueryParam("limit", equalTo("1000"))
      .willReturn(WireMock.okJson(jsonObject.encode()).withStatus(201)));

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post(blUsersLoginEndpoint)
      .then()
      .statusCode(404);

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/users"))
      .withQueryParam("query", equalTo("username==\"" + USERNAME + "\"")));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/perms/users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID)));

    WireMock.verify(1, postRequestedFor(urlPathEqualTo(authLoginEndpoint)));

    WireMock.verify(1, getRequestedFor(urlPathEqualTo("/service-points-users"))
      .withQueryParam("query", equalTo("userId==" + USER_ID))
      .withQueryParam("limit", equalTo("1000")));

    WireMock.getAllServeEvents().stream()
      .map(ServeEvent::getRequest)
      .forEach(this::verifyHeaders);
  }

  @Test
  public void testPostBlUsersSettingsMyprofilePassword() {
    JsonObject valid = new JsonObject().put("result", "valid");
    WireMock.stubFor(
      WireMock.post("/password/validate")
        .willReturn(WireMock.okJson(valid.encode()).withStatus(200))
        .withRequestBody(passwordValidateRequestMatcher())
    );

    WireMock.stubFor(
      WireMock.post(URL_AUTH_UPDATE)
        .willReturn(WireMock.noContent())
    );

    UpdateCredentials credentials = new UpdateCredentials();
    credentials.setUserId(USER_ID);
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");
    credentials.setNewPassword(USER_PASSWORD);

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post("/bl-users/settings/myprofile/password")
      .then()
      .statusCode(204);

    WireMock.getAllServeEvents().stream()
      .map(ServeEvent::getRequest)
      .forEach(this::verifyHeaders);
  }

  @Test
  public void testPostBlUsersPasswordResetReset() {
    PasswordResetAction passwordResetAction = new PasswordResetAction();
    passwordResetAction.setUserId(USER_ID);
    passwordResetAction.setExpirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));

    WireMock.stubFor(
      WireMock.get("/authn/password-reset-action/" + RESET_PASSWORD_ACTION_ID)
        .willReturn(WireMock.okJson(JsonObject.mapFrom(passwordResetAction).encode()))
    );

    WireMock.stubFor(
      WireMock.get("/users/" + USER_ID)
        .willReturn(WireMock.okJson(toJson(new User().withId(USER_ID))))
    );

    JsonObject valid = new JsonObject().put("result", "valid");
    WireMock.stubFor(
      WireMock.post("/password/validate")
        .willReturn(WireMock.okJson(valid.encode()).withStatus(200))
        .withRequestBody(passwordValidateRequestMatcher())
    );

    JsonObject isNewPassword = new JsonObject().put("isNewPassword", false);
    WireMock.stubFor(
      WireMock.post(URL_AUT_RESET_PASSWORD)
        .willReturn(WireMock.okJson(isNewPassword.encode()).withStatus(201))
    );

    WireMock.stubFor(
      WireMock.post("/notify")
        .willReturn(WireMock.created())
    );

    String header = "x-forWarded-FOR";
    RestAssured
      .given()
      .spec(spec)
      .header(new Header(header, IP))
      .header(new Header("x-okapi-token", buildMockJwtToken()))
      .body(JsonObject.mapFrom(new PasswordReset().withNewPassword(USER_PASSWORD)).encode())
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(204);

    WireMock.getAllServeEvents().stream()
      .map(ServeEvent::getRequest)
      .forEach(this::verifyHeaders);
  }

  private String buildMockJwtToken() {
    JsonObject payload = new JsonObject()
      .put("sub", UNDEFINED_USER_NAME + RESET_PASSWORD_ACTION_ID);
    byte[] bytes = payload.encode().getBytes(StandardCharsets.UTF_8);
    return String.format(JWT_TOKEN_PATTERN, TOKEN, Base64.getEncoder().encodeToString(bytes), "sig");
  }

  private void verifyHeaders(LoggedRequest request) {
    HttpHeaders headers = request.getHeaders();

    String url = request.getUrl();
    if (isContainsSpecifiedUrls(url)) {
      assertTrue(headers.getHeader(BLUsersAPI.X_FORWARDED_FOR_HEADER).containsValue(IP));
      assertTrue(headers.getHeader(USER_AGENT).hasValueMatching(WireMock.matching(".+")));
    }

    assertTrue(headers.getHeader(RestVerticle.OKAPI_HEADER_TENANT).containsValue(TENANT));
    assertTrue(headers.getHeader(RestVerticle.OKAPI_HEADER_TOKEN).hasValueMatching(WireMock.containing(TOKEN)));
  }

  private boolean isContainsSpecifiedUrls(String url) {
    return url.contains(URL_AUT_RESET_PASSWORD)
      || url.contains(URL_AUTH_UPDATE)
      || url.contains(URL_AUTH_LOGIN_LEGACY);
  }

  private StringValuePattern passwordValidateRequestMatcher() {
    JsonObject passwordEntity = new JsonObject()
      .put("password", USER_PASSWORD)
      .put("userId", USER_ID);
    return WireMock.equalToJson(passwordEntity.toString());
  }

  private String toJson(Object object) {
    return JsonObject.mapFrom(object).toString();
  }
}
