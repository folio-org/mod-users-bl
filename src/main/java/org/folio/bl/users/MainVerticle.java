/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.bl.users;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import org.folio.bl.util.RecordNotFoundException;
import org.folio.bl.util.ResultNotUniqueException;


/**
 *
 * @author kurt
 */


public class MainVerticle extends AbstractVerticle {
  
  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private String dummyOkapiURL = null;
  
  private final Logger logger = LoggerFactory.getLogger("mod-users-bl");
  public void start(Future<Void> future) {
    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();
    
    String logLevel = System.getProperty("log.level", null);
    if(logLevel != null) {
      try {
        org.apache.log4j.Logger l4jLogger;
        l4jLogger = org.apache.log4j.Logger.getLogger("mod-users-bl");
        l4jLogger.getParent().setLevel(org.apache.log4j.Level.toLevel(logLevel));
      } catch(Exception e) {
        logger.error("Unable to set log level: " + e.getMessage());
      }
    }
    
    final int port = Integer.parseInt(System.getProperty("port", "8081"));
    
    dummyOkapiURL = System.getProperty("dummy.okapi.url", null);
    
    router.get("/by-id/:id").handler(this::handleRetrieve);
    router.get("/:username").handler(this::handleRetrieve);
  }
  
  private void handleRetrieve(RoutingContext context) {
    String id = context.request().getParam("id");
    String username = context.request().getParam("username");
    String okapiURL;
    if(dummyOkapiURL == null) {
      okapiURL = context.request().getHeader(OKAPI_URL_HEADER);
    } else {
      okapiURL = dummyOkapiURL;
    }
    String tenant = context.request().getHeader(OKAPI_TENANT_HEADER);
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);

    Future<JsonObject> userRecordFuture;
    
    if(id != null) {
      userRecordFuture = getRecordByKey(id, tenant, okapiURL + "/users", token);
    } else {
      userRecordFuture = getRecordByQuery("username", username, "users", tenant, okapiURL + "/users", token);
    }
    
    userRecordFuture.setHandler(userRecordResult -> {
      if(userRecordResult.failed()) {
        if(userRecordResult.cause() instanceof RecordNotFoundException) {
          context.response().setStatusCode(404);
          if(id != null) {
            context.response().end("Unable to locate user with id '" + id + "'");
          } else {
            context.response().end("Unable to find user '" + username + "'");
          }
        } else if(userRecordResult.cause() instanceof ResultNotUniqueException) {
          context.response()
                  .setStatusCode(400)
                  .end("Username '" + username + "' is not unique");
        } else {
          context.response()
                  .setStatusCode(500)
                  .end("An error has occurred, please contact your system administrator");
          logger.debug("Error retrieving user record: " + userRecordResult.cause().getLocalizedMessage());
        }
      } else {
        JsonObject masterResponseObject = new JsonObject();
        Future<JsonObject> credentialsObjectFuture;
        Future<JsonObject> permissionsObjectFuture;
        
        credentialsObjectFuture = Future.succeededFuture(new JsonObject()); //for testing
        permissionsObjectFuture = Future.succeededFuture(new JsonObject()); //for testing
        
        CompositeFuture compositeFuture = CompositeFuture.all(credentialsObjectFuture, permissionsObjectFuture);
        compositeFuture.setHandler(compositeResult -> {
          if(compositeResult.failed()) {
            logger.debug("Error resolving composite future: " + compositeResult.cause().getLocalizedMessage());
            context.response()
                    .setStatusCode(400)
                    .end("An error occurred");
          } else {
            masterResponseObject.put("user", userRecordResult.result());
            masterResponseObject.put("credentials", credentialsObjectFuture.result());
            masterResponseObject.put("permissions", permissionsObjectFuture.result());
            context.response()
                    .setStatusCode(200)
                    .end(masterResponseObject.encode());
          }
        });
      }
    });
    
  }
  
  
  private Future<JsonObject> getRecordByQuery(String field, String value, String resultKey, String tenant, String moduleURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    String queryString = "?query=" + field + "=" + value;
    HttpClientRequest request = httpClient.getAbs(moduleURL + queryString);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .handler(queryResult -> {
              if(queryResult.statusCode() != 200) {
                queryResult.bodyHandler(body -> {
                  future.fail("Got status code "+ queryResult.statusCode() + ": " + body.toString());
                });
              } else {
                queryResult.bodyHandler(body -> {
                  JsonObject result;
                  try {
                    result = new JsonObject(body.toString());
                  } catch(Exception e) {
                    future.fail("Unable to parse body as JSON: " + e.getLocalizedMessage());
                    return;
                  }
                  if(result.getInteger("totalRecords") < 1) {
                    future.fail(new RecordNotFoundException("No record found for query '" + queryString + "'"));
                  } else if(result.getInteger("totalRecords") > 1) {
                    future.fail(new ResultNotUniqueException("'" + queryString + "' returns multiple results"));
                  } else {
                    JsonObject record = result.getJsonArray(resultKey).getJsonObject(0);
                    future.complete(record);
                  }
                });
              }
            })
            .end();
    return future;  
  }
  
  private Future<JsonObject> getRecordByKey(String key, String tenant, String moduleURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    HttpClientRequest request = httpClient.getAbs(moduleURL + "/" + key);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .handler(queryResult -> {
              if(queryResult.statusCode() != 200) {
                if(queryResult.statusCode() == 404) {
                  future.fail(new RecordNotFoundException("No record found with key '"+ key + "'"));
                } else {
                  queryResult.bodyHandler(body -> {
                    future.fail("Got status code " + queryResult.statusCode() + ": " + body.toString());
                  });
                }
              } else {
                queryResult.bodyHandler(body -> {
                  JsonObject result;
                  try {
                    result = new JsonObject(body.toString());
                  } catch(Exception e) {
                    future.fail("Unable to parse body as JSON: " + e.getLocalizedMessage());
                    return;
                  }
                  future.complete(result);
                });
              }
            })
            .end();
    return future;
  }
}
