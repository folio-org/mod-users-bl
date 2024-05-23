package org.folio.rest;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.http.HttpStatus;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.PercentCodec;
import org.folio.util.StringUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.folio.rest.MockOkapi.getToken;
import static org.folio.rest.MockOkapi.getTokenWithoutUserId;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;


@RunWith(VertxUnitRunner.class)
public class BLUsersAPITest {
  static Vertx vertx;
  private static RequestSpecification okapi;
  private static int okapiPort;
  /** port of BLUsersAPI */
  private static int port;

  private static final Logger logger = LogManager.getLogger(BLUsersAPITest.class);

  private static final String NOT_EXPIRED_PASSWORD_RESET_ACTION_ID = "5ac3b82d-a7d4-43a0-8285-104e84e01274";
  private static final String EXPIRED_PASSWORD_RESET_ACTION_ID = "16423d10-f403-4de5-a6e9-8e0add61bf5b";
  private static final String NONEXISTENT_PASSWORD_RESET_ACTION_ID = "41a9a229-6492-46ae-b9fc-017ba1e2705d";
  private static final String FAKE_USER_ID_PASSWORD_RESET_ACTION_ID = "2a604a02-666c-44b6-b238-e81f379f1eb4";
  private static final String FAKE_PASSWORD_RESET_ACTION_ID_WITH_INCORRECT_USER_ID = "2a604a02-666c-44b6-b238-e81f379f1e77";
  private static final String USER_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String USER_ID_2 = "0bb4f26d-e073-4f93-afbc-dcc24fd88812";
  private static final String USER_ID_3 = "0bb4f26d-e073-4f93-afbc-dcc24fd88813";
  private static final String USER_ID_4 = "0bb4f26d-e073-4f93-afbc-dcc24fd88814";
  private static final String USER_ID_5 = "0bb4f26d-e073-4f93-afbc-dcc24fd88815";
  private static final String FAKE_USER_ID = "f2216cfc-4abb-4f54-85bb-4945c9fd91cb";
  private static final String UNDEFINED_USER_NAME = "UNDEFINED_USER__RESET_PASSWORD_";
  private static final String JWT_TOKEN_PATTERN = "%s.%s.%s";
  private static final String JWT_TOKEN = "dummyJwt";

  public static final String USER_TEST_NAME = "userTest";
  public static final String USER_TEST_ID = "3ceb2f0e-50a7-4b82-8d78-5e1e87d0318f";

  @BeforeClass
  public static void before(TestContext context) {
    vertx = Vertx.vertx();
    vertx.exceptionHandler(context.exceptionHandler());

    okapiPort = NetworkUtils.nextFreePort();
    DeploymentOptions okapiOptions = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", okapiPort));
    TestUtil.deploy(MockOkapi.class, okapiOptions, vertx, context);

    port = NetworkUtils.nextFreePort();
    DeploymentOptions usersBLOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port).putNull(HttpClientMock2.MOCK_MODE));
    TestUtil.deploy(RestVerticle.class, usersBLOptions, vertx, context);

    RestAssured.port = port;
    RequestSpecBuilder builder = new RequestSpecBuilder();
    builder.addHeader("X-Okapi-URL", "http://localhost:" + okapiPort);
    builder.addHeader("X-Okapi-Tenant", "supertenant");
    builder.addHeader("X-Okapi-Token", token("supertenant", "maxi"));
    okapi = builder.build();
    insertData();
  }

  private static String token(String tenant, String user) {
    JsonObject payload = new JsonObject()
        .put("sub", user)
        .put("tenant", tenant);
    byte[] bytes = payload.encode().getBytes(StandardCharsets.UTF_8);
    return "dummyJwt." + Base64.getEncoder().encodeToString(bytes) + ".sig";
  }

  private static void insertData() {
    JsonObject userPost = new JsonObject()
        .put("username", "maxi")
        .put("id", USER_ID)
        .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
        .put("active", true)
        .put("personal", new JsonObject().put("email", "maxi@maxi.com"));
    given().body(userPost.encode()).
    when().post("http://localhost:" + okapiPort + "/users").
    then().statusCode(201);

    userPost = new JsonObject()
      .put("username", USER_TEST_NAME)
      .put("id", USER_TEST_ID)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "user@gmail.org").put("lastName", "userLastName"));
    given()
      .body(userPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/users")
      .then()
      .statusCode(201);

    userPost = new JsonObject()
      .put("username", "tst")
      .put("id", "b4b5e97a-0a99-4db9-97df-5fdf406ec74d")
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "user@gmail.org").put("lastName", "userLastName"));
    given()
      .body(userPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/users")
      .then()
      .statusCode(201);

    userPost = new JsonObject()
      .put("username", "twin1")
      .put("id", USER_ID_2)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "twin1@maxi.com").put("phone", "123-12-123"));
    given().body(userPost.encode()).
      when().post("http://localhost:" + okapiPort + "/users").
      then().statusCode(201);

    userPost = new JsonObject()
      .put("username", "twin1")
      .put("id", USER_ID_3)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "twin1@maxi.com").put("phone", "123-12-123"));
    given().body(userPost.encode()).
      when().post("http://localhost:" + okapiPort + "/users").
      then().statusCode(201);

    userPost = new JsonObject()
      .put("username", "userinactive")
      .put("id", USER_ID_4)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", false)
      .put("personal", new JsonObject().put("email", "userinactive@maxi.com").put("phone", "123-12-123"));
    given().body(userPost.encode()).
      when().post("http://localhost:" + okapiPort + "/users").
      then().statusCode(201);

    userPost = new JsonObject()
        .put("username", "quo\"te")
        .put("id", USER_ID_5)
        .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
        .put("active", true)
        .put("personal", new JsonObject().put("email", "quote@example.com").put("phone", "123-12-123"));
      given().body(userPost.encode()).
        when().post("http://localhost:" + okapiPort + "/users").
        then().statusCode(201);

    JsonObject groupPost = new JsonObject().put("group", "staff")
        .put("desc", "people running the library")
        .put("id", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d");
    given().body(groupPost.encode()).
    when().post("http://localhost:" + okapiPort + "/groups").
    then().statusCode(201);

    JsonObject permission = new JsonObject().
        put("permissionName", "ui-checkin.all").
        put("displayName", "Check in: All permissions").
        put("id", "604a6236-1c9d-4681-ace1-a0dd1bba5058");
    String [] users = { USER_ID, USER_ID_2, USER_ID_3, USER_ID_4, USER_ID_5 };
    for (var user : users) {
      JsonObject permsUsersPost = new JsonObject()
          .put("permissions", new JsonArray().add(permission))
          .put("userId", user);
      given().body(permsUsersPost.encode()).
      when().post("http://localhost:" + okapiPort + "/perms/users").
      then().statusCode(201);
    }

    given().body(new JsonObject()
      .put("module", "USERSBL")
      .put("configName", "fogottenData")
      .put("code", "userName")
      .put("description", "userName")
      .put("default", "false")
      .put("enabled", "true")
      .put("value", "username").encode()).
      when().post("http://localhost:" + okapiPort + "/configurations/entries").
      then().statusCode(201);

    given().body(new JsonObject()
      .put("module", "USERSBL")
      .put("configName", "fogottenData")
      .put("code", "phoneNumber")
      .put("description", "personal.phone, personal.mobilePhone")
      .put("default", "false")
      .put("enabled", "true")
      .put("value", "personal.phone, personal.mobilePhone").encode()).
      when().post("http://localhost:" + okapiPort + "/configurations/entries").
      then().statusCode(201);

    given().body(new JsonObject()
      .put("module", "USERSBL")
      .put("configName", "fogottenData")
      .put("code", "email")
      .put("description", "personal.email")
      .put("default", "false")
      .put("enabled", "true")
      .put("value", "personal.email").encode()).
      when().post("http://localhost:" + okapiPort + "/configurations/entries").
      then().statusCode(201);

    given().body(new JsonObject()
      .put("module", "USERSBL")
      .put("configName", "resetPassword")
      .put("code", "FOLIO_HOST")
      .put("description", "folio host")
      .put("default", "false")
      .put("enabled", "true")
      .put("value", "http://localhost").encode()).
      when().post("http://localhost:" + okapiPort + "/configurations/entries").
      then().statusCode(201);


    given().body(new JsonObject()
      .put("id", NOT_EXPIRED_PASSWORD_RESET_ACTION_ID)
      .put("userId", USER_ID)
      .put("expirationTime", Instant.now().plus(1, ChronoUnit.DAYS))
      .encode())
      .when().post("http://localhost:" + okapiPort + "/authn/password-reset-action")
      .then().statusCode(201);

    given().body(new JsonObject()
      .put("id", EXPIRED_PASSWORD_RESET_ACTION_ID)
      .put("userId", USER_ID)
      .put("expirationTime", Instant.now().minus(1, ChronoUnit.DAYS))
      .encode())
      .when().post("http://localhost:" + okapiPort + "/authn/password-reset-action")
      .then().statusCode(201);

    given().body(new JsonObject()
      .put("id", FAKE_USER_ID_PASSWORD_RESET_ACTION_ID)
      .put("userId", FAKE_USER_ID)
      .put("expirationTime", Instant.now().minus(1, ChronoUnit.DAYS))
      .encode())
      .when().post("http://localhost:" + okapiPort + "/authn/password-reset-action")
      .then().statusCode(201);

    given().body(new JsonObject()
      .put("id", FAKE_PASSWORD_RESET_ACTION_ID_WITH_INCORRECT_USER_ID)
      .put("userId", "77604a02-666c-44b6-b238-e81f379f1e77")
      .put("expirationTime", Instant.now().plus(1, ChronoUnit.DAYS))
      .encode())
      .when().post("http://localhost:" + okapiPort + "/authn/password-reset-action")
      .then().statusCode(201);
  }

  @AfterClass
  public static void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void getBlUsers(TestContext context) {
    given().
            spec(okapi).port(port).
    when().
            get("/bl-users").
    then().
            statusCode(200).
            body("compositeUsers.size()", equalTo(7),
                 "compositeUsers[0].users.username", equalTo("maxi"));
  }

  @Test
  public void getBlUserByUserNameWithEmptyPermissionSet(TestContext context) {
    given().
      spec(okapi).port(port).
      when().
      get("/bl-users/by-username/tst").
      then().
      statusCode(200).
      body("permissions.permissions", hasSize(0));
  }

  @Test
  public void getBlUsersSelf(TestContext context) {
    Header header = new Header(RestVerticle.OKAPI_HEADER_TOKEN, getToken(USER_ID, "maxi", "diku"));
    given().
        spec(okapi).port(port).header(header).
      when().
        get("/bl-users/_self").
      then().
        statusCode(200);
  }

  @Test
  public void getBlUsersSelfWithoutToken(TestContext context) {
    Header header = new Header(RestVerticle.OKAPI_HEADER_TOKEN, "");
    given().
      spec(okapi).port(port).header(header).
      when().
      get("/bl-users/_self").
      then().
      statusCode(400);
  }

  @Test
  public void getBlUsersSelfWithoutUserId(TestContext context) {
    Header header = new Header(RestVerticle.OKAPI_HEADER_TOKEN, getTokenWithoutUserId("maxi", "diku"));
    given().
      spec(okapi).port(port).header(header).
      when().
      get("/bl-users/_self").
      then().
      statusCode(200);
  }

  @Test
  public void getBlUsersCql(TestContext context) {
    given().
      spec(okapi).port(port).
    when().
      get("/bl-users?query=" + StringUtil.urlEncode("username==minni OR username==maxi")).
    then().
      statusCode(200).
      body("compositeUsers.size()", equalTo(1),
           "compositeUsers[0].users.username", equalTo("maxi"));
  }

  @Test
  public void getBlUsersCqlQuote(TestContext context) {
    given().
      spec(okapi).port(port).
    when().
      get("/bl-users?query=" + PercentCodec.encode("username==\"quo\\\"te\"")).
    then().
      statusCode(200).
      body("compositeUsers.size()", equalTo(1),
           "compositeUsers[0].users.username", equalTo("quo\"te"));
  }

  @Test
  public void postBlUsersForgottenPassword(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "maxi").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(204);

    //find cross tenant user by id
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", USER_TEST_NAME).encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(204);

    //by username
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "twin1").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.password.found.multiple.users"));

    //by email
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "twin1@maxi.com").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.password.found.multiple.users"));

    //by phone
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "123-12-123").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.password.found.multiple.users"));

    //inactive
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "userinactive").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/password").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.password.found.inactive"));
  }

  @Test
  public void postBlUsersForgottenUsername(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "maxi@maxi.com").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/username").
      then().
      statusCode(204);

    //by email
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "twin1@maxi.com").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/username").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.username.found.multiple.users"));

    //by phone
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "123-12-123").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/username").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.username.found.multiple.users"));

    //inactive
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("id", "userinactive@maxi.com").encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/forgotten/username").
      then().
      statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY).
      body("errors[0].code", equalTo("forgotten.password.found.inactive"));
  }

  @Test
  public void postBlUsersUpdatePasswordFail(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("username", "superuser")
        .put("password", "12345")
        .put("newPassword", "123456")
        .put("userId", "99999999-9999-4999-9999-999999999999")
        .encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/settings/myprofile/password").
      then().
      statusCode(400);
  }

  @Test
  public void postBlUsersUpdatePasswordInvalidOldPassword(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("username", "superuser")
        .put("password", "123456")
        .put("newPassword", "1q2w3E!190")
        .put("userId", "99999999-9999-4999-9999-999999999999")
        .encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/settings/myprofile/password").
      then().
      statusCode(401);
  }

  @Test
  public void postBlUsersUpdatePasswordOk(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("username", "superuser")
        .put("password", "12345")
        .put("newPassword", "1q2w3E!190")
        .put("userId", "99999999-9999-4999-9999-999999999999")
        .encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/settings/myprofile/password").
      then().
      statusCode(204);
  }

  @Test
  public void postBlUsersUpdatePasswordNoUser(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("username", "superuser")
        .put("password", "12345")
        .put("newPassword", "1q2w3E!190")
        .put("userId", "99999999-9999-4999-9999-999999999991")
        .encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/settings/myprofile/password").
      then().
      statusCode(500);
  }

  @Test
  public void postBlUsersPasswordResetValidate() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-url", "http://localhost:" + okapiPort))
      .header(new Header("x-okapi-token", buildToken(NOT_EXPIRED_PASSWORD_RESET_ACTION_ID)))
      .header(new Header("x-okapi-tenant", "supertenant"))
      .when()
      .post("/bl-users/password-reset/validate")
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void postBlUsersPasswordResetValidateExpiredAction() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-url", "http://localhost:" + okapiPort))
      .header(new Header("x-okapi-token", buildToken(EXPIRED_PASSWORD_RESET_ACTION_ID)))
      .header(new Header("x-okapi-tenant", "supertenant"))
      .when()
      .post("/bl-users/password-reset/validate")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postBlUsersPasswordResetValidateNonexistentAction() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-url", "http://localhost:" + okapiPort))
      .header(new Header("x-okapi-token", buildToken(NONEXISTENT_PASSWORD_RESET_ACTION_ID)))
      .header(new Header("x-okapi-tenant", "supertenant"))
      .when()
      .post("/bl-users/password-reset/validate")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postBlUsersPasswordResetValidateFakeUserId() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-url", "http://localhost:" + okapiPort))
      .header(new Header("x-okapi-token", buildToken(FAKE_USER_ID_PASSWORD_RESET_ACTION_ID)))
      .header(new Header("x-okapi-tenant", "supertenant"))
      .when()
      .post("/bl-users/password-reset/validate")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postBlUsersPasswordResetValidateWithIncorrectUser() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-url", "http://localhost:" + okapiPort))
      .header(new Header("x-okapi-token", buildToken(FAKE_PASSWORD_RESET_ACTION_ID_WITH_INCORRECT_USER_ID)))
      .header(new Header("x-okapi-tenant", "supertenant"))
      .when()
      .post("/bl-users/password-reset/validate")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postPasswordReset() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .header(new Header("x-okapi-token", buildToken(NOT_EXPIRED_PASSWORD_RESET_ACTION_ID)))
      .port(port)
      .body(new JsonObject()
        .put("newPassword", "1q2w3E!190").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void postPasswordResetIncorrectPassword() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .header(new Header("x-okapi-token", buildToken(NOT_EXPIRED_PASSWORD_RESET_ACTION_ID)))
      .port(port)
      .body(new JsonObject()
        .put("newPassword", "1q2w3E!190ggggg").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void postPasswordResetWithIncorrectUser() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "77604a02-666c-44b6-b238-e81f379f1e77"))
      .header(new Header("x-okapi-token", buildToken(FAKE_PASSWORD_RESET_ACTION_ID_WITH_INCORRECT_USER_ID)))
      .port(port)
      .body(new JsonObject()
        .put("newPassword", "1q2w3E!190").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postPasswordResetWithIncorrectToken() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "77604a02-666c-44b6-b238-e81f379f1e77"))
      .header(new Header("x-okapi-token", buildIncorrectToken(FAKE_PASSWORD_RESET_ACTION_ID_WITH_INCORRECT_USER_ID)))
      .port(port)
      .body(new JsonObject()
        .put("newPassword", "1q2w3E!190").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postPasswordResetInvalidPassword() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .body(new JsonObject()
        .put("resetPasswordActionId", NOT_EXPIRED_PASSWORD_RESET_ACTION_ID)
        .put("newPassword", "123456").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postPasswordResetNonexistentAction() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .body(new JsonObject()
        .put("resetPasswordActionId", NONEXISTENT_PASSWORD_RESET_ACTION_ID)
        .put("newPassword", "1q2w3E!190").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void postPasswordResetExpiredAction() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .body(new JsonObject()
        .put("resetPasswordActionId", EXPIRED_PASSWORD_RESET_ACTION_ID)
        .put("newPassword", "1q2w3E!190").encode())
      .accept("text/plain")
      .contentType("application/json")
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void deleteUserNoTransactions() {
    String uuid = UUID.randomUUID().toString();
    JsonObject userPost = new JsonObject()
      .put("username", "userToDelete")
      .put("id", uuid)
      .put("barcode", "1234")
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "maxi@maxi.com").put("lastName", "foobar"));
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .body(userPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/users")
      .then()
      .statusCode(201);

    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .when()
      .delete("/bl-users/by-id/" + uuid)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void deleteUserWithTransactionsNoSuccess() {
    String blockId = UUID.randomUUID().toString();
    JsonObject manualBlockPost = new JsonObject()
      .put("id", blockId)
      .put("userId", USER_ID);
    given().body(manualBlockPost.encode()).
      when().post("http://localhost:" + okapiPort + "/manualblocks").
      then().statusCode(201);

    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .when()
      .delete("/bl-users/by-id/" + USER_ID)
      .then()
      .statusCode(HttpStatus.SC_CONFLICT)
      .body("blocks", equalTo(1))
      .body("hasOpenTransactions", equalTo(true));

    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .when()
      .delete("http://localhost:" + okapiPort + "/manualblocks/" + blockId)
      .then()
      .statusCode(204);
  }

  @Test
  public void deleteUserNotExistent() {
    given()
      .spec(okapi)
      .header(new Header("x-okapi-user-id", "99999999-9999-4999-9999-999999999999"))
      .port(port)
      .when()
      .delete("/bl-users/by-id/" + UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private String buildToken(String passwordResetActionId) {
    JsonObject payload = new JsonObject()
      .put("sub", UNDEFINED_USER_NAME + passwordResetActionId);
    byte[] bytes = payload.encode().getBytes(StandardCharsets.UTF_8);
    return String.format(JWT_TOKEN_PATTERN, JWT_TOKEN, Base64.getEncoder().encodeToString(bytes), "sig");
  }

  private String buildIncorrectToken(String passwordResetActionId) {
    JsonObject payload = new JsonObject()
      .put("sub", "INCORRECT_\"" + passwordResetActionId);
    byte[] bytes = payload.encode().getBytes(StandardCharsets.UTF_8);
    return String.format(JWT_TOKEN_PATTERN, JWT_TOKEN, Base64.getEncoder().encodeToString(bytes), "sig");
  }
}
