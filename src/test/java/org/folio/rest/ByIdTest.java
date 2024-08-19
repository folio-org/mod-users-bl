package org.folio.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.folio.rest.client.impl.CirculationStorageModuleClientImpl.REQUEST_PREFERENCES_ENDPOINT;
import static org.folio.rest.client.impl.LoginAuthnCredentialsClientImpl.AUTHN_CREDENTIALS_ENDPOINT;
import static org.folio.rest.client.impl.PermissionModuleClientImpl.MOD_PERMISSION_ENDPOINT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.StringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@WireMockTest
class ByIdTest {

  private static final String userId = "9d4e1ab4-5ee5-49eb-831b-36d9b16576bc";
  private static final String patronGroup = "c1c9bb5c-bcf9-4f1d-9b0e-c3b270f99e21";
  private static String urlById;

  private String okapiUrl;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    var port = NetworkUtils.nextFreePort();
    urlById = "http://localhost:" + port + "/bl-users/by-id";
    var deploymentOptions = new DeploymentOptions()
        .setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class, deploymentOptions)
    .onComplete(vtc.succeedingThenComplete());
  }

  @BeforeEach
  void before(WireMockRuntimeInfo wiremock) {
    okapiUrl = "http://localhost:" + wiremock.getHttpPort();
  }

  @Test
  void getUserWithExpandPermissions() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubFor(get("/groups/" + patronGroup).willReturn(okJson("id", patronGroup, "group", "myGroup")));
    stubFor(get("/service-points-users?query=userId==" + userId + "&limit=1000")
        .willReturn(okJson("servicePointsUsers", new JsonArray())));
    stubFor(get("/perms/users?query=userId==" + userId).willReturn(okJson(
        new JsonObject().put("permissionUsers", new JsonArray().add(
            new JsonObject().put("id", userId).put("userId", userId))))));
    stubFor(get("/perms/users/" + userId + "/permissions?expanded=true&full=true").willReturn(okJson(
        new JsonObject().put("permissionNames", new JsonArray().add("read").add("write")))));

    whenGetById()
      .then()
      .statusCode(200)
      .body("user.patronGroup", is(patronGroup))
      .body("permissions.permissions", contains("read", "write"));
  }

  @Test
  void deleteUserThatDoesNotExist() {
    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  void deleteUserThatExists() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubOpenTransactions(0);
    stubFor(delete("/users/" + userId).willReturn(status(204)));

    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void deleteUserThatExistsAndSuccessfullyDeletedOraphanRecords() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubOpenTransactions(0);
    stubFor(delete("/users/" + userId).willReturn(status(204)));

    //Stubbing for deleting request-preference-storage
    String requestPreferenceId = UUID.randomUUID().toString();
    JsonObject requestPreferenceStorageRes = new JsonObject().put("totalRecords", 1)
      .put("requestPreferences", new JsonArray().add(new JsonObject().put("id", requestPreferenceId)));
    stubFor(get(REQUEST_PREFERENCES_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(requestPreferenceStorageRes)));
    stubFor(delete(REQUEST_PREFERENCES_ENDPOINT + "/" + requestPreferenceId)
      .willReturn(status(204)));

    //Stubbing for deleting login Auth Credentials
    stubFor(delete(AUTHN_CREDENTIALS_ENDPOINT + "?userId=" + userId)
      .willReturn(status(204)));

    //Stubbing for deleting /perms/users
    String permsRandomId = UUID.randomUUID().toString();
    JsonObject permsUsersRes = new JsonObject().put("totalRecords", 1)
      .put("permissionUsers", new JsonArray().add(new JsonObject().put("id", permsRandomId)));
    stubFor(get(MOD_PERMISSION_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(permsUsersRes)));
    stubFor(delete(MOD_PERMISSION_ENDPOINT + "/" + permsRandomId)
      .willReturn(status(204)));

    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void deleteUserThatExistsAndFailedToGetOraphanRecords() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubOpenTransactions(0);
    stubFor(delete("/users/" + userId).willReturn(status(204)));

    //Stubbing for deleting request-preference-storage
    String requestPreferenceId = UUID.randomUUID().toString();
    JsonObject requestPreferenceStorageRes = new JsonObject().put("totalRecords", 0)
      .put("requestPreferences", new JsonArray());
    stubFor(get(REQUEST_PREFERENCES_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(requestPreferenceStorageRes).withStatus(200)));
    stubFor(delete(REQUEST_PREFERENCES_ENDPOINT + "/" + requestPreferenceId)
      .willReturn(status(404).withBody("Not found")));

    //Stubbing for deleting login Auth Credentials
    stubFor(delete(AUTHN_CREDENTIALS_ENDPOINT + "?userId=" + userId)
      .willReturn(status(404)));

    //Stubbing for deleting /perms/users
    String permsRandomId = UUID.randomUUID().toString();
    JsonObject permsUsersRes = new JsonObject().put("totalRecords", 0)
      .put("permissionUsers", new JsonArray());
    stubFor(get(MOD_PERMISSION_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(permsUsersRes)));
    stubFor(delete(MOD_PERMISSION_ENDPOINT + "/" + permsRandomId)
      .willReturn(status(404).withBody("Not found")));

    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void deleteUserThatExistsAndFailedToDeleteOraphanRecords() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubOpenTransactions(0);
    stubFor(delete("/users/" + userId).willReturn(status(204)));

    //Stubbing for deleting request-preference-storage
    String requestPreferenceId = UUID.randomUUID().toString();
    JsonObject requestPreferenceStorageRes = new JsonObject().put("totalRecords", 1)
      .put("requestPreferences", new JsonArray().add(new JsonObject().put("id", requestPreferenceId)));
    stubFor(get(REQUEST_PREFERENCES_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(requestPreferenceStorageRes)));
    stubFor(delete(REQUEST_PREFERENCES_ENDPOINT + "/" + requestPreferenceId)
      .willReturn(status(404).withBody("Not found")));

    //Stubbing for deleting login Auth Credentials
    stubFor(delete(AUTHN_CREDENTIALS_ENDPOINT + "?userId=" + userId)
      .willReturn(status(404).withBody("Not found")));

    //Stubbing for deleting /perms/users
    String permsRandomId = UUID.randomUUID().toString();
    JsonObject permsUsersRes = new JsonObject().put("totalRecords", 1)
      .put("permissionUsers", new JsonArray().add(new JsonObject().put("id", permsRandomId)));
    stubFor(get(MOD_PERMISSION_ENDPOINT + "?query=" +
      StringUtil.urlEncode("userId==" + StringUtil.cqlEncode(userId)))
      .willReturn(okJson(permsUsersRes)));
    stubFor(delete(MOD_PERMISSION_ENDPOINT + "/" + permsRandomId)
      .willReturn(status(404).withBody("Not found")));

    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  void deleteUserWithOpenTransactions() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubOpenTransactions(1);

    whenDeleteById()
      .then()
      .statusCode(HttpStatus.SC_CONFLICT);
  }

  private void stubOpenTransactions(int totalRecords) {
    var paths = List.of(
        "/loan-storage/loans",
        "/request-storage/requests",
        "/accounts",
        "/manualblocks",
        "/proxiesfor");
    paths.forEach(path ->
        stubFor(get(urlPathEqualTo(path)).willReturn(okJson("totalRecords", totalRecords))));
  }

  private Response whenGetById() {
    return given().
        log().ifValidationFails().
        headers("X-Okapi-Tenant", "diku").
        headers("X-Okapi-Url", okapiUrl).
        when().get(urlById + "/" + userId + "?expandPermissions=true");
  }

  private Response whenDeleteById() {
    return given().
        log().ifValidationFails().
        headers("X-Okapi-Tenant", "diku").
        headers("X-Okapi-Token", "a.b.c").
        headers("X-Okapi-Url", okapiUrl).
        when().delete(urlById + "/" + userId);
  }

  private static ResponseDefinitionBuilder okJson(JsonObject jsonObject) {
    return WireMock.okJson(jsonObject.encode());
  }

  private static ResponseDefinitionBuilder okJson(String key, Object value) {
    return WireMock.okJson(new JsonObject().put(key, value).encode());
  }

  private static ResponseDefinitionBuilder okJson(String key, String value, String key2, String value2) {
    return WireMock.okJson(new JsonObject().put(key, value).put(key2, value2).encode());
  }
}
