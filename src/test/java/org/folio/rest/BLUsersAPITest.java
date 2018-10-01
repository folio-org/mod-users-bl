package org.folio.rest;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class BLUsersAPITest {
  Vertx vertx;
  RequestSpecification okapi;
  int okapiPort;
  /** port of BLUsersAPI */
  int port;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    vertx.exceptionHandler(context.exceptionHandler());

    okapiPort = NetworkUtils.nextFreePort();
    DeploymentOptions okapiOptions = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", okapiPort));
    vertx.deployVerticle(MockOkapi.class.getName(), okapiOptions, context.asyncAssertSuccess());

    insertData();

    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", port).putNull(HttpClientMock2.MOCK_MODE));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());

    RestAssured.port = port;

    RequestSpecBuilder builder = new RequestSpecBuilder();
    builder.addHeader("X-Okapi-URL", "http://localhost:" + okapiPort);
    builder.addHeader("X-Okapi-Tenant", "supertenant");
    builder.addHeader("X-Okapi-Token", token("supertenant", "maxi"));
    okapi = builder.build();
  }

  private String token(String tenant, String user) {
    JsonObject payload = new JsonObject()
        .put("sub", user)
        .put("tenant", tenant);
    byte[] bytes = payload.encode().getBytes(StandardCharsets.UTF_8);
    return "dummyJwt." + Base64.getEncoder().encodeToString(bytes) + ".sig";
  }

  private void insertData() {
    String userId = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
    JsonObject userPost = new JsonObject()
        .put("username", "maxi")
        .put("id", userId)
        .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
        .put("active", true)
        .put("personal", new JsonObject().put("email", "maxi@maxi.com"));
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
    JsonObject permsUsersPost = new JsonObject()
        .put("permissions", new JsonArray().add(permission))
        .put("userId", userId);
    given().body(permsUsersPost.encode()).
    when().post("http://localhost:" + okapiPort + "/perms/users").
    then().statusCode(201);

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

  }

  @After
  public void after(TestContext context) {
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
            body("compositeUsers[0].users.username", equalTo("maxi"));
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
  }

  @Test
  public void postBlUsersUpdatePasswordFail(TestContext context) {
    given().
      spec(okapi).port(port).
      body(new JsonObject().put("username", "superuser")
        .put("password", "12345")
        .put("newPassword", "123456")
        .put("userId", "99999999-9999-9999-9999-999999999999")
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
        .put("userId", "99999999-9999-9999-9999-999999999999")
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
        .put("userId", "99999999-9999-9999-9999-999999999999")
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
        .put("userId", "99999999-9999-9999-9999-999999999991")
        .encode()).
      accept("text/plain").
      contentType("application/json").
      when().
      post("/bl-users/settings/myprofile/password").
      then().
      statusCode(500);
  }
}
