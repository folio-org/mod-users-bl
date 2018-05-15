package org.folio.rest;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import org.folio.rest.TestUtil.WrappedResponse;
import org.folio.rest.client.TenantClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
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

  private static int mockOkapiPort;
  private static int mockUsersBLPort;
  private static Vertx vertx;
  private static TestUtil testUtil;

  private final String bfrederiId = "a53ae072-45b5-4cc5-9437-2e47b38b50b7";
  private final String lkoId = "5e5df008-62f8-4820-bdb7-c32b1181ab10";
  private final String mphillipsId = "e6c8eb71-9e9a-499b-bb3c-82e49d198dbf";
  private final String testGroupId = "c7ec21df-b00f-48c4-9b31-e01b5207689f";
  private String bfrederiPermId = UUID.randomUUID().toString();
  private String lkoPermId = UUID.randomUUID().toString();
  private String mphillipsPermId = UUID.randomUUID().toString();

  JsonArray userList = new JsonArray()
          .add(new JsonObject()
            .put("username", "bfrederi")
            .put("id", bfrederiId)
            .put("active", true)
            .put("patronGroup", testGroupId)
          )
          .add(new JsonObject()
            .put("username", "lko")
            .put("id", lkoId)
            .put("active", true)
            .put("patronGroup", testGroupId)
          )
          .add(new JsonObject()
            .put("username", "mphillips")
            .put("id", mphillipsId)
            .put("active", true)
            .put("patronGroup", testGroupId)
          );

  JsonArray groupList = new JsonArray()
          .add(new JsonObject()
            .put("group", "test")
            .put("desc", "Test Group")
            .put("id", testGroupId)
          );

  JsonArray permUserList = new JsonArray()
          .add(new JsonObject()
            .put("id", bfrederiPermId)
            .put("userId", bfrederiId)
            .put("permissions", new JsonArray()
              .add("gamma.all")
            )
          )
          .add(new JsonObject()
            .put("id", lkoPermId)
            .put("userId", lkoId)
            .put("permissions", new JsonArray()
              .add("beta.all")
            )
          )
          .add(new JsonObject()
            .put("id", mphillipsPermId)
            .put("userId", mphillipsId)
            .put("permissions", new JsonArray()
              .add("alpha.all")
            )
          );

  JsonArray permissionList = new JsonArray()
          .add(new JsonObject()
            .put("permissionName", "alpha.all")
            .put("subPermissions", new JsonArray()
              .add("alpha.a")
              .add("alpha.b")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "alpha.a")
            .put("subPermissions", new JsonArray()
              .add("beta.a")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "alpha.b")
            .put("subPermissions", new JsonArray()
              .add("beta.b")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "beta.all")
            .put("subPermissions", new JsonArray()
              .add("beta.a")
              .add("beta.b")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "beta.a")
            .put("subPermissions", new JsonArray()
              .add("gamma.a")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "beta.b")
            .put("subPermissions", new JsonArray()
              .add("gamma.b")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "gamma.all")
            .put("subPermissions", new JsonArray()
              .add("gamma.b")
              .add("gamma.a")
            )
          )
          .add(new JsonObject()
            .put("permissionName", "gamma.a")
            .put("subPermissions", new JsonArray())
          )
          .add(new JsonObject()
            .put("permissionName", "gamma.b")
            .put("subPermissions", new JsonArray())
          );

  @BeforeClass
  public static void setupClass(TestContext context) {
    vertx = Vertx.vertx();
    testUtil = new TestUtil();
    Async async = context.async();
    mockOkapiPort = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions().setConfig(
            new JsonObject().put("http.port", mockOkapiPort));

    vertx.deployVerticle(MockOkapi.class.getName(), options, res -> {
      if(res.failed()) {
        context.fail(res.cause());
      } else {
        mockUsersBLPort = NetworkUtils.nextFreePort();
        DeploymentOptions usersBLOptions = new DeploymentOptions()
                .setConfig(new JsonObject()
                        .put("http.port", mockUsersBLPort)
                        .putNull(HttpClientMock2.MOCK_MODE)
                );
        vertx.deployVerticle(RestVerticle.class.getName(), usersBLOptions,
                res2 -> {
          if(res2.failed()) {
            context.fail(res2.cause());
          } else {
            async.complete();
          }
        });
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
  public void testMatcher(TestContext context) {
    String uri = "/perms/users/223d60af-a137-41e8-90d8-20c8a5991639/permissions";
    String endpoint = "/perms/users";
    Matcher matcher = MockOkapi.parseMockUri(uri, endpoint);
    if(!matcher.matches()) { context.fail("No matches for uri"); }
    else if(matcher.group(1) == null) {
      context.fail(String.format("id from uri %s is null", uri));
    } else if(matcher.group(2) == null) {
      context.fail(String.format("No remainder for uri %s", uri));
    } else {
      context.async().complete();
    }
 }

  @Test
  public void doSequentialTests(TestContext context) {
    Async async = context.async();
    Future<WrappedResponse> startFuture;
    startFuture = getEmptyUsers(context).compose(w -> {
      return postNewUser(context);
    }).compose(w -> {
      return getNewUser(context, w.getJson().getString("id"));
    }).compose( w -> {
      return getEmptyPermsUsers(context);
    }).compose(w -> {
      return loadDataArray(context, "http://localhost:" + mockOkapiPort + "/users", userList);
    }).compose(w -> {
      return loadDataArray(context, "http://localhost:" + mockOkapiPort + "/groups", groupList);
    }).compose(w -> {
      return loadDataArray(context, "http://localhost:" + mockOkapiPort + "/perms/users", permUserList);
    }).compose(w -> {
      return loadDataArray(context, "http://localhost:" + mockOkapiPort + "/perms/permissions", permissionList);
    }).compose( w -> {
      return getUserPerms(context, mphillipsPermId, new JsonArray()
              .add("gamma.a")
              .add("beta.b")
              .add("alpha.a"));
    })
      .compose(w -> {
      return getUserByQuery(context, "bfrederi");
    })
      .compose(w -> {
      return getBLUserList(context);
    }).compose(w -> {
      return getSingleBLUser(context, mphillipsId);
    }).compose(w -> {
      List<String> expectedPerms = new ArrayList<>();
      expectedPerms.add("gamma.a");
      expectedPerms.add("beta.b");
      return getSingleBLUserExpandedPerms(context, mphillipsId,
              expectedPerms);
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
    String url = "http://localhost:" + mockOkapiPort + "/users";
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
    String url = "http://localhost:" + mockOkapiPort + "/users";
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

  private Future<WrappedResponse> getNewUser(TestContext context, String id) {
    Future<WrappedResponse> future = Future.future();
    String url = "http://localhost:" + mockOkapiPort + "/users/" + id;
    testUtil.doRequest(vertx, url, GET, null, null).setHandler(res -> {
      if(res.failed()) { future.fail(res.cause()); } else {
        context.assertEquals(res.result().getCode(), 200);
        future.complete(res.result());
      }
    });
    return future;
  }

  private Future<WrappedResponse> getEmptyPermsUsers(TestContext context) {
    Future<WrappedResponse> future = Future.future();
    String url = "http://localhost:" + mockOkapiPort + "/perms/users";
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

  private Future<WrappedResponse> loadDataArray(TestContext context, String url,
          JsonArray dataList) {
    System.out.println("Adding data to endpoint " + url + "\n");
    Future<WrappedResponse> future = Future.future();
    //String url = "http://localhost:" + mockOkapiPort + "/users";
    List<Future> futureList = new ArrayList<>();
    for(Object ob : dataList) {
      Future<WrappedResponse> responseFuture = testUtil.doRequest(vertx, url,
              POST, null, ((JsonObject)ob).encode());
      futureList.add(responseFuture);
    }
    CompositeFuture compositeFuture = CompositeFuture.all(futureList);
    compositeFuture.setHandler(res -> {
      if(res.failed()) { future.fail(res.cause()); } else {
        boolean failed = false;
        for(Future fut : futureList) {
          if(fut.failed()) {
            future.fail(fut.cause());
            failed = true;
            break;
          } else if( ((WrappedResponse)fut.result()).getCode() != 201 ) {
            future.fail(String.format("Expected 201, got '%s': %s",
                    ((WrappedResponse)fut.result()).getCode(),
                    ((WrappedResponse)fut.result()).getBody()));
            failed = true;
            break;
          }
        }
        if(!failed) {
          future.complete();
        }
      }
    });
    return future;
  }

  private Future<WrappedResponse> getUserPerms(TestContext context,
          String permUserId, JsonArray expectedPerms) {
    System.out.println("Retrieving perms for perm user id " + permUserId + "\n");
    Future<WrappedResponse> future = Future.future();
    String url = String.format("http://localhost:%s/perms/users/%s/permissions?expanded=true",
            mockOkapiPort, permUserId);
    testUtil.doRequest(vertx, url, GET, null, null).setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else {
        JsonArray permissions = null;
        boolean failed = false;
        try {
          permissions = res.result().getJson().getJsonArray("permissionNames");
        } catch(Exception e) {
          failed = true;
          future.fail("No 'permissionNames' field in " + res.result().getBody());
        }
        if(permissions != null) {
          for(Object perm : expectedPerms) {
            if(!permissions.contains(perm)) {
              future.fail(String.format("Expected perm '%s', but not present", perm));
              failed = true;
              break;
            }
          }
        } else {
          if(!failed) {
            failed = true;
            future.fail("permissions array is null");
          }
        }
        if(!failed) {
          future.complete(res.result());
        }
      }
    });
    return future;
  }

  private Future<WrappedResponse> getBLUserList(TestContext context) {
    Future<WrappedResponse> future = Future.future();
    String url = String.format("http://localhost:%s/bl-users", mockUsersBLPort);
    CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
    headers.add("X-Okapi-URL", "http://localhost:" + mockOkapiPort);
    testUtil.doRequest(vertx, url, GET, headers, null).setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else if(res.result().getCode() != 200) {
        future.fail("Expected 200, got code " + res.result().getCode());
      } else {
        future.complete(res.result());
      }
    });
    return future;
  }

  private Future<WrappedResponse> getSingleBLUser(TestContext context,
          String userId) {
    Future<WrappedResponse> future = Future.future();
     String url = String.format("http://localhost:%s/bl-users/by-id/%s",
             mockUsersBLPort, userId);
     CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
     headers.add("X-Okapi-URL", "http://localhost:" + mockOkapiPort);
     testUtil.doRequest(vertx, url, GET, headers, null).setHandler(res -> {
       if(res.failed()) {
         future.fail(res.cause());
       } else {
         if(res.result().getCode() != 200) {
           future.fail("Expected 200, got code " + res.result().getCode());
         } else {
           future.complete(res.result());
         }
       }
     });
     return future;
  }

  private Future<WrappedResponse> getSingleBLUserExpandedPerms(TestContext context,
          String userId, List<String> expectedPerms) {
    Future<WrappedResponse> future = Future.future();
     String url = String.format(
             "http://localhost:%s/bl-users/by-id/%s?include=perms&expandPermissions=true",
             mockUsersBLPort, userId);
     CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
     headers.add("X-Okapi-URL", "http://localhost:" + mockOkapiPort);
     testUtil.doRequest(vertx, url, GET, headers, null).setHandler(res -> {
       if(res.failed()) {
         future.fail(res.cause());
       } else {
         if(res.result().getCode() != 200) {
           future.fail("Expected 200, got code " + res.result().getCode());
         } else {
           String missingPerm = null;
           for(String perm : expectedPerms) {
             boolean foundPerm = false;
             JsonArray permissions = res.result().getJson()
                     .getJsonObject("permissions").getJsonArray("permissions");
             for(Object permOb : permissions) {
               if(((JsonObject)permOb).getString("permissionName").equals(perm)) {
                foundPerm = true;
                break;
               }
             }
             if(!foundPerm) {
               missingPerm = perm;
               break;
             }
           }
           if(missingPerm != null) {
             future.fail(String.format("Could not find expected permission '%s'",
                     missingPerm));
           } else {
            future.complete(res.result());
           }
         }
       }
     });
     return future;
  }

  private Future<WrappedResponse> getUserByQuery(TestContext context, String username) {
    Future<WrappedResponse> future = Future.future();
    String url = String.format("http://localhost:%s/users?query=username==%s",
            mockOkapiPort, username);
    testUtil.doRequest(vertx, url, GET, null, null).setHandler(res -> {
      if(res.failed()) {
        future.fail(res.cause());
      } else {
        if(res.result().getCode() != 200) {
          future.fail(String.format("Expected code 200, got %s: %s",
                  res.result().getCode(), res.result().getBody()));
        } else {
          if(res.result().getJson() == null) {
            future.fail(String.format("%s returned null json", res.result().getBody()));
          } else if(res.result().getJson().getInteger("totalRecords") != 1) {
            future.fail(String.format("Expected 1 result, got %s",
                    res.result().getJson().getInteger("totalRecords")));
          } else if(!res.result().getJson().getJsonArray("users")
                  .getJsonObject(0).getString("username").equals(username)) {
            future.fail(String.format("Username does not equal %s", username));
          } else {
            future.complete(res.result());
          }
        }
      }
    });
    return future;
  }
}



