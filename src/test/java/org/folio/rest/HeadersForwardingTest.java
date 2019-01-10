package org.folio.rest;

import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.folio.rest.impl.BLUsersAPI;
import org.folio.rest.jaxrs.model.LoginCredentials;
import org.folio.rest.jaxrs.model.PasswordReset;
import org.folio.rest.jaxrs.model.PasswordResetAction;
import org.folio.rest.jaxrs.model.UpdateCredentials;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.HttpHeaders;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RunWith(VertxUnitRunner.class)
public class HeadersForwardingTest {

  private static final String TENANT = "test";
  private static final String USERNAME = "maxi";
  private static final String USER_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String RESET_PASSWORD_ACTION_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String IP = "216.3.128.12";

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
      .addHeader(BLUsersAPI.OKAPI_URL_HEADER, "http://localhost:" + mockServer.port())
      .build();

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess());
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


    WireMock.stubFor(
      WireMock.get("/users?query=username=" + USERNAME)
      .willReturn(WireMock.okJson(users.encode()))
    );

    WireMock.stubFor(
      WireMock.post("/authn/login")
        .willReturn(WireMock.okJson(JsonObject.mapFrom(credentials).encode()).withStatus(201))
    );

    JsonObject permsUsersPost = new JsonObject()
      .put("permissionUsers", new JsonArray().add(new JsonObject()));

    WireMock.stubFor(
      WireMock.get("/perms/users?query=userId==" + USER_ID)
      .willReturn(WireMock.okJson(permsUsersPost.encode()).withStatus(201))
    );

    JsonObject jsonObject = new JsonObject()
      .put("servicePointsUsers", new JsonArray());

    WireMock.stubFor(
      WireMock.get("/service-points-users?query=userId==" + USER_ID)
      .willReturn(WireMock.okJson(jsonObject.encode()).withStatus(201))
    );

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post("/bl-users/login")
      .then()
      .statusCode(201);

    WireMock.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/authn/login"))
        .withHeader(HttpHeaders.USER_AGENT, WireMock.matching(".+"))
        .withHeader(BLUsersAPI.X_FORWARDED_FOR_HEADER, WireMock.equalTo(IP))
    );
  }

  @Test
  public void testPostBlUsersSettingsMyprofilePassword() {
    JsonObject valid = new JsonObject().put("result", "valid");
    WireMock.stubFor(
      WireMock.post("/password/validate")
      .willReturn(WireMock.okJson(valid.encode()).withStatus(200))
    );

    WireMock.stubFor(
      WireMock.post("/authn/update")
        .willReturn(WireMock.noContent())
    );

    UpdateCredentials credentials = new UpdateCredentials();
    credentials.setUserId(USER_ID);
    credentials.setUsername(USERNAME);
    credentials.setPassword("password");
    credentials.setNewPassword("Newpwd!10");

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(credentials).encode())
      .when()
      .post("/bl-users/settings/myprofile/password")
      .then()
      .statusCode(204);

    WireMock.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/authn/update"))
        .withHeader(HttpHeaders.USER_AGENT, WireMock.matching(".+"))
        .withHeader(BLUsersAPI.X_FORWARDED_FOR_HEADER, WireMock.equalTo(IP))
    );
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
        .willReturn(WireMock.okJson(new JsonObject().encode()))
    );

    JsonObject valid = new JsonObject().put("result", "valid");
    WireMock.stubFor(
      WireMock.post("/password/validate")
        .willReturn(WireMock.okJson(valid.encode()).withStatus(200))
    );

    JsonObject isNewPassword = new JsonObject().put("isNewPassword", false);
    WireMock.stubFor(
      WireMock.post("/authn/reset-password")
        .willReturn(WireMock.okJson(isNewPassword.encode()).withStatus(201))
    );

    WireMock.stubFor(
      WireMock.post("/notify")
        .willReturn(WireMock.created())
    );

    PasswordReset entity = new PasswordReset();
    entity.setNewPassword("Newpwd!10");
    entity.setResetPasswordActionId(RESET_PASSWORD_ACTION_ID);

    RestAssured
      .given()
      .spec(spec)
      .header(new Header(BLUsersAPI.X_FORWARDED_FOR_HEADER, IP))
      .body(JsonObject.mapFrom(entity).encode())
      .when()
      .post("/bl-users/password-reset/reset")
      .then()
      .statusCode(204);

    WireMock.verify(
      WireMock.postRequestedFor(WireMock.urlEqualTo("/authn/reset-password"))
        .withHeader(HttpHeaders.USER_AGENT, WireMock.matching(".+"))
        .withHeader(BLUsersAPI.X_FORWARDED_FOR_HEADER, WireMock.equalTo(IP))
    );
  }
}
