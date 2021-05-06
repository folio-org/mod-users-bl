package org.folio.rest;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class OpenTransactionsTest {

  static Vertx vertx;
  private static RequestSpecification okapi;
  private static int okapiPort;
  private static int port;

  private static final String USER_ID = "0bb4f26d-e073-4f93-afbc-dcc24fd88810";
  private static final String USER_BARCODE = "12345";
  private static final String USER_NAME = "maxi";
  private static final String LOAN_ID = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca01";
  private static final String REQUEST_ID = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca02";
  private static final String ACCOUNT_ID = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca03";
  private static final String PROXY_ID_1 = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca04";
  private static final String PROXY_ID_2 = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca05";
  private static final String MANUAL_BLOCK_ID = "dfa12cc0-b82e-4554-9cb0-4f5bb1fdca06";

  @BeforeClass
  public static void before(TestContext context) {
    vertx = Vertx.vertx();
    vertx.exceptionHandler(context.exceptionHandler());

    okapiPort = NetworkUtils.nextFreePort();
    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", okapiPort));
    vertx.deployVerticle(MockOkapi.class.getName(), okapiOptions, context.asyncAssertSuccess());

    port = NetworkUtils.nextFreePort();
    DeploymentOptions usersBLOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", port).putNull(HttpClientMock2.MOCK_MODE));
    vertx.deployVerticle(RestVerticle.class.getName(), usersBLOptions, context.asyncAssertSuccess());

    RestAssured.port = port;
    RequestSpecBuilder builder = new RequestSpecBuilder();
    builder.addHeader("X-Okapi-URL", "http://localhost:" + okapiPort);
    builder.addHeader("X-Okapi-Tenant", "supertenant");
    builder.addHeader("X-Okapi-Token", token("supertenant", "maxi"));
    okapi = builder.build();
    insertData();
  }

  @Before
  public void cleanUpTransactionData(TestContext context) {
    TestUtil testUtil = new TestUtil();
    Future<TestUtil.WrappedResponse> deleteLoanFuture =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/loan-storage/loans/" + LOAN_ID, HttpMethod.DELETE, null, null);
    Future<TestUtil.WrappedResponse> deleteRequestsFuture =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/request-storage/requests/" + REQUEST_ID, HttpMethod.DELETE, null, null);
    Future<TestUtil.WrappedResponse> deleteAccountsFuture =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/accounts/" + ACCOUNT_ID, HttpMethod.DELETE, null, null);
    Future<TestUtil.WrappedResponse> deleteProxy1Future =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/proxiesfor/" + PROXY_ID_1, HttpMethod.DELETE, null, null);
    Future<TestUtil.WrappedResponse> deleteProxy2Future =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/proxiesfor/" + PROXY_ID_2, HttpMethod.DELETE, null, null);
    Future<TestUtil.WrappedResponse> deleteManualBlocksFuture =
      testUtil.doRequest(vertx, "http://localhost:" + okapiPort + "/manualblocks/" + MANUAL_BLOCK_ID, HttpMethod.DELETE, null, null);

    Async async = context.async();
    CompositeFuture.all(deleteLoanFuture, deleteRequestsFuture, deleteAccountsFuture, deleteProxy1Future, deleteProxy2Future, deleteManualBlocksFuture)
      .onSuccess(asyncResult -> async.complete())
      .onFailure(context::fail);
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
      .put("username", USER_NAME)
      .put("id", USER_ID)
      .put("barcode", USER_BARCODE)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "maxi@maxi.com").put("lastName", "foobar"));
    given()
      .body(userPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/users")
      .then()
      .statusCode(201);
  }

  @Test
  public void getTransactionsOfUserHasRequests() {
    JsonObject requestPost = new JsonObject()
      .put("id", REQUEST_ID)
      .put("requesterId", USER_ID)
      .put("itemId", UUID.randomUUID().toString())
      .put("status", "Open - Not yet filled");
    given()
      .body(requestPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/request-storage/requests")
      .then()
      .statusCode(201);

    given()
      .spec(okapi).port(port)
      .when()
      .get("/bl-users/by-id/" + USER_ID + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(true),
        "loans", equalTo(0),
        "requests", equalTo(1),
        "feesFines", equalTo(0),
        "proxies", equalTo(0),
        "blocks", equalTo(0),
        "userId", equalTo(USER_ID),
        "userBarcode", equalTo(USER_BARCODE));

    given()
      .spec(okapi).port(port)
      .when()
      .get("/bl-users/by-username/" + USER_NAME + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(true),
        "loans", equalTo(0),
        "requests", equalTo(1),
        "feesFines", equalTo(0),
        "proxies", equalTo(0),
        "blocks", equalTo(0),
        "userId", equalTo(USER_ID),
        "userBarcode", equalTo(USER_BARCODE));
  }

  @Test
  public void getTransactionsOfUserHasFeesFines() {
    JsonObject feesFinesPost = new JsonObject()
      .put("id", ACCOUNT_ID)
      .put("userId", USER_ID)
      .put("status", new JsonObject().put("name", "Open"));
    given()
      .body(feesFinesPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/accounts")
      .then()
      .statusCode(201);

    given()
      .spec(okapi).port(port)
      .when()
      .get("/bl-users/by-id/" + USER_ID + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(true),
        "loans", equalTo(0),
        "requests", equalTo(0),
        "feesFines", equalTo(1),
        "proxies", equalTo(0),
        "blocks", equalTo(0),
        "userId", equalTo(USER_ID));
  }

  @Test
  public void getTransactionsOfUserHasMultiTransactions() {
    JsonObject manualBlockPost = new JsonObject()
      .put("id", MANUAL_BLOCK_ID)
      .put("userId", USER_ID);
    given()
      .body(manualBlockPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/manualblocks")
      .then()
      .statusCode(201);

    JsonObject feesFinesPost = new JsonObject()
      .put("id", ACCOUNT_ID)
      .put("userId", USER_ID)
      .put("status", new JsonObject().put("name", "Open"));
    given()
      .body(feesFinesPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/accounts")
      .then()
      .statusCode(201);

    JsonObject proxiesPostOne = new JsonObject()
      .put("id", PROXY_ID_1)
      .put("userId", USER_ID);
    given()
      .body(proxiesPostOne.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/proxiesfor")
      .then()
      .statusCode(201);

    JsonObject proxiesPostTwo = new JsonObject()
      .put("id", PROXY_ID_2)
      .put("userId", UUID.randomUUID().toString())
      .put("proxyUserId", USER_ID);
    given()
      .body(proxiesPostTwo.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/proxiesfor")
      .then().statusCode(201);

    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-id/" + USER_ID + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(true),
        "loans", equalTo(0),
        "requests", equalTo(0),
        "feesFines", equalTo(1),
        "proxies", equalTo(2),
        "blocks", equalTo(1),
        "userId", equalTo(USER_ID));
  }

  @Test
  public void getTransactionsOfUserHasBlocks() {
    JsonObject manualBlockPost = new JsonObject()
      .put("id", MANUAL_BLOCK_ID)
      .put("userId", USER_ID);
    given()
      .body(manualBlockPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/manualblocks")
      .then()
      .statusCode(201);

    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-id/" + USER_ID + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(true),
        "loans", equalTo(0),
        "requests", equalTo(0),
        "feesFines", equalTo(0),
        "proxies", equalTo(0),
        "blocks", equalTo(1),
        "userId", equalTo(USER_ID));
  }

  @Test
  public void getTransactionsOfUserHasNone() {
    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-id/" + USER_ID + "/open-transactions")
      .then()
      .statusCode(200)
      .body("hasOpenTransactions", equalTo(false),
        "loans", equalTo(0),
        "requests", equalTo(0),
        "feesFines", equalTo(0),
        "proxies", equalTo(0),
        "blocks", equalTo(0),
        "userId", equalTo(USER_ID));
  }

  @Test
  public void getTransactionsUserNotPresent() {
    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-id/" + UUID.randomUUID() + "/open-transactions")
      .then()
      .statusCode(404);

    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-username/noUser/open-transactions")
      .then()
      .statusCode(404);
  }

  @Test
  public void getTransactionsMultiUser() {
    JsonObject userPost = new JsonObject()
      .put("username", USER_NAME)
      .put("id", UUID.randomUUID().toString())
      .put("barcode", USER_BARCODE)
      .put("patronGroup", "b4b5e97a-0a99-4db9-97df-4fdf406ec74d")
      .put("active", true)
      .put("personal", new JsonObject().put("email", "maxi@maxi.com").put("lastName", "foobar"));
    given()
      .body(userPost.encode())
      .when()
      .post("http://localhost:" + okapiPort + "/users")
      .then()
      .statusCode(201);

    given()
      .spec(okapi)
      .port(port)
      .when()
      .get("/bl-users/by-username/" + USER_NAME + "/open-transactions")
      .then()
      .statusCode(500);
  }
}
