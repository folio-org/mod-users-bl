package org.folio.rest;

import static io.restassured.RestAssured.given;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.contains;

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
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@WireMockTest
class ByIdTest {

  private static final String userId = "9d4e1ab4-5ee5-49eb-831b-36d9b16576bc";
  private static final String patronGroup = "c1c9bb5c-bcf9-4f1d-9b0e-c3b270f99e21";
  private static final String servicePointId = "e8b5a1b2-781a-48e5-a401-73f193d239e7";
  private static String urlById;

  private String okapiUrl;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    var port = NetworkUtils.nextFreePort();
    urlById = "http://localhost:" + port + "/bl-users/by-id/" + userId;
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
  void emptyPermissonArrays() {
    stubFor(get("/users/" + userId).willReturn(okJson("id", userId, "patronGroup", patronGroup)));
    stubFor(get("/groups/" + patronGroup).willReturn(okJson("id", patronGroup, "group", "myGroup")));
    stubFor(get("/service-points-users?query=userId==" + userId + "&limit=1000")
        .willReturn(okJson("servicePointsUsers", new JsonArray())));
    stubFor(get("/perms/users?query=userId==" + userId).willReturn(okJson(
        new JsonObject().put("permissionUsers", new JsonArray().add(
            new JsonObject().put("id", userId).put("userId", userId))))));
    stubFor(get("/perms/users/" + userId + "/permissions?expanded=true&full=true").willReturn(okJson(
        new JsonObject().put("permissionNames", new JsonArray().add("read").add("write")))));

    whenGetById().
    then().statusCode(200)
    .body("user.patronGroup", is(patronGroup))
    .body("permissions.permissions", contains("read", "write"));
  }

  private Response whenGetById() {
    return given().
        log().ifValidationFails().
        headers("X-Okapi-Tenant", "diku").
        headers("X-Okapi-Url", okapiUrl).
        when().get(urlById + "?expandPermissions=true");
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
