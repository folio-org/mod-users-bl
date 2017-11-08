/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Map;
import org.folio.rest.TestUtil.WrappedResponse;
import org.folio.rest.client.TenantClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author kurt
 */
@RunWith(VertxUnitRunner.class)
public class MockOkapiTest {
  
  private static int port;
  private static Vertx vertx;
  private static TestUtil testUtil;
  
  @BeforeClass
  public static void setupClass(TestContext context) {
    vertx = Vertx.vertx();
    testUtil = new TestUtil();
    Async async = context.async();
    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("port", port));
    vertx.deployVerticle(MockOkapi.class.getName(), options, res -> {
      if(res.failed()) {
        context.fail(res.cause());
      } else {
       async.complete();
      }
    });
  }
  
  @AfterClass
  public static void teardownClass(TestContext context) {
    context.async().complete();
  }
  
  @Before
  public void beforeTest(TestContext context) {
    context.async().complete();
  }
  
  @After
  public void afterTest(TestContext context) {
    context.async().complete();
  }
  
  @Test
  public void doSequentialTests(TestContext context) {
    Async async = context.async();
    Future<WrappedResponse> startFuture;
    startFuture = getEmptyUsers(context).compose(w -> {
      return postNewUser(context);
    }).compose( w -> {
      return getEmptyPermsUsers(context);
    });    
    startFuture.setHandler(res -> {
      if(res.succeeded()) {
        async.complete();
      } else {
        Throwable root = res.cause();
        while(res.cause().getCause() != null) {
          root = res.cause().getCause();
        }
        root.printStackTrace();
        context.fail(res.cause());
      }
    });
  }
  
  private Future<WrappedResponse> getEmptyUsers(TestContext context) {
    System.out.println("Getting an empty user set\n");
    Future<WrappedResponse> future = Future.future();
    String url = "http://localhost:" + port + "/users";
    Future<WrappedResponse> futureResponse = testUtil.doRequest(vertx, url,
            HttpMethod.GET, null, null);
    futureResponse.setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else {
        context.assertEquals(res.result().getCode(), 200);
        context.assertNotNull(res.result().getJson());
        context.assertEquals(res.result().getJson().getJsonArray("users").size(), 0);
        context.assertEquals(res.result().getJson().getInteger("totalRecords"), 0);
        future.complete(res.result());
      }
    });
    return future;
  }
  
  private Future<WrappedResponse> postNewUser(TestContext context) {
    System.out.println("Adding a new user\n");
    Future<WrappedResponse> future = Future.future();
    String url = "http://localhost:" + port + "/users";
    JsonObject userPost = new JsonObject().put("username", "bongo")
            .put("id", "0bb4f26d-e073-4f93-afbc-dcc24fd88810")
            .put("active", true);
    Future<WrappedResponse> futureResponse = testUtil.doRequest(vertx, url, 
            HttpMethod.POST, null, userPost.encode());
    futureResponse.setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else {
        context.assertEquals(res.result().getCode(), 201);
        future.complete(res.result());
      }
    });
    return future;
  }
  
  private Future<WrappedResponse> getEmptyPermsUsers(TestContext context) {
    Future<WrappedResponse> future = Future.future();
    String url = "http://localhost:" + port + "/perms/users";
    Future<WrappedResponse> futureResponse = testUtil.doRequest(vertx, url,
            HttpMethod.GET, null, null);
    futureResponse.setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else {
        context.assertEquals(res.result().getCode(), 200);
        context.assertNotNull(res.result().getJson());
        context.assertEquals(res.result().getJson().getJsonArray("permissionUsers").size(), 0);
        context.assertEquals(res.result().getJson().getInteger("totalRecords"), 0);
        future.complete(res.result());
      }
    });
    return future;
  }
  
}
  
  
  