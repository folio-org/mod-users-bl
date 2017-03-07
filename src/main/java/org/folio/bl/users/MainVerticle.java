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
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.RoutingContext;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import org.folio.bl.util.RecordNotFoundException;
import org.folio.bl.util.ResultNotUniqueException;
import java.lang.Integer;


/**
 *
 * @author kurt
 */


public class MainVerticle extends AbstractVerticle {
   
  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private String dummyOkapiURL = null;
  private static String URL_ROOT = "/bl-users";
  
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
    
    router.get(URL_ROOT + "/by-id/:id").handler(this::handleRetrieve);
    router.get(URL_ROOT + "/:username").handler(this::handleRetrieve);
    router.put().handler(BodyHandler.create());
    router.put(URL_ROOT + "/by-id/:id").handler(this::handleModify);
    router.put(URL_ROOT + "/:username").handler(this::handleModify);
    router.post().handler(BodyHandler.create());
    router.post(URL_ROOT).handler(this::handleCreate);

    server.requestHandler(router::accept).listen(port, serverResult -> {
      if(serverResult.failed()) {
        future.fail(serverResult.cause());
      } else {
        future.complete();
      }
    });
    logger.debug("users-bl module listening on port " + port);
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
          logger.error("Error retrieving user record: " + userRecordResult.cause().getLocalizedMessage());
        }
      } else {
        JsonObject masterResponseObject = new JsonObject();
        String retrievedUsername = userRecordResult.result().getString("username");

        Future<JsonObject> credentialsObjectFuture;
        Future<JsonObject> permissionsObjectFuture;
        
        credentialsObjectFuture = this.getCredentialsRecord(retrievedUsername, tenant, okapiURL, token);
        permissionsObjectFuture = this.getPermissionsRecord(retrievedUsername, tenant, okapiURL, token);
        
        logger.debug("Creating composite future");
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
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(masterResponseObject.encode());
          }
        });
      }
    });
    
  }

  private void handleModify(RoutingContext context) {
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
    //We're going to need to look up the user record to get both the id and the username
    if(username != null) {
      //userRecordFuture = Future.succeededFuture(new JsonObject().put("username", username));
      userRecordFuture = getRecordByQuery("username", username, "users", tenant, okapiURL + "/users", token);
    } else {
      userRecordFuture = getRecordByKey(id, tenant, okapiURL + "/users", token);
    }
    userRecordFuture.setHandler(userRecordResult->{
      if(userRecordResult.failed()) {
        if(userRecordResult.cause() instanceof RecordNotFoundException) {
          context.response().setStatusCode(404)
            .end("No user found with id '" + id + "'");
        } else {
          context.response()
            .setStatusCode(500)
            .end("Server error");
          logger.error("Error retrieving user record: " + userRecordResult.cause().getLocalizedMessage());
        }
      } else {
        JsonObject userRecord = userRecordResult.result();
        logger.debug("Got userRecord: " + userRecord.encode());
        JsonObject entity = context.getBodyAsJson();
        String userUsername = userRecord.getString("username");
        String userId = userRecord.getString("id");
        JsonObject userPayload = entity.getJsonObject("user");
        JsonObject credentialsPayload = entity.getJsonObject("credentials");
        JsonObject permissionsPayload = entity.getJsonObject("permissions");

        Future<JsonObject> userFuture;
        Future<JsonObject> credentialsFuture;
        Future<JsonObject> permissionsFuture;

        if(userPayload != null) {
          userFuture = putUserRecord(userId, userPayload, tenant, okapiURL, token);
        } else {
          userFuture = Future.succeededFuture(null);
        }

        if(credentialsPayload != null) {
          credentialsFuture = putCredentialsRecord(userUsername, credentialsPayload, tenant, okapiURL, token);
        } else {
          credentialsFuture = Future.succeededFuture(null);
        }

        if(permissionsPayload != null) {
          permissionsFuture = putPermissionsRecord(userUsername, permissionsPayload, tenant, okapiURL, token);
        } else {
          permissionsFuture = Future.succeededFuture(null);
        }

        CompositeFuture compositeFuture = CompositeFuture.all(userFuture, credentialsFuture, permissionsFuture);
        compositeFuture.setHandler(compositeResult -> {
          if(compositeResult.failed()) {
            context.response()
              .setStatusCode(500)
              .end("Server error");
              logger.error("Composite future failed: " + compositeResult.cause().getLocalizedMessage());
          } else {
            JsonObject masterResultRecord = new JsonObject();
            masterResultRecord.put("user", userFuture.result());
            masterResultRecord.put("credentials", credentialsFuture.result());
            masterResultRecord.put("permissions", permissionsFuture.result());
            context.response()
              .setStatusCode(200)
              .end(masterResultRecord.encode());
          }
        });
      }
    });
  }

  private void handleCreate(RoutingContext context) {
    String okapiURL;
    if(dummyOkapiURL == null) {
      okapiURL = context.request().getHeader(OKAPI_URL_HEADER);
    } else {
      okapiURL = dummyOkapiURL;
    }
    String tenant = context.request().getHeader(OKAPI_TENANT_HEADER);
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);
    JsonObject entity = context.getBodyAsJson();
    if(entity == null) {
      context.response()
        .setStatusCode(400)
        .end("POST body must be a JSON object");
        return;
    }
    JsonObject userPayload = entity.getJsonObject("user");
    JsonObject credentialsPayload = entity.getJsonObject("credentials");
    JsonObject permissionsPayload = entity.getJsonObject("permissions");
  
    Future<JsonObject> userFuture;
    Future<JsonObject> credentialsFuture;
    Future<JsonObject> permissionsFuture;

    if(userPayload != null) {
      userFuture = postUserRecord(userPayload, tenant, okapiURL, token);
    } else {
      userFuture = Future.succeededFuture(null);
    }

    if(credentialsPayload != null) {
      credentialsFuture = postCredentialsRecord(credentialsPayload, tenant, okapiURL, token);
    } else {
      credentialsFuture = Future.succeededFuture(null);
    }

    if(permissionsPayload != null) {
      permissionsFuture = postPermissionsRecord(permissionsPayload, tenant, okapiURL, token);
    } else {
      permissionsFuture = Future.succeededFuture(null);
    }

    CompositeFuture compositeFuture = CompositeFuture.all(userFuture, credentialsFuture, permissionsFuture);
    compositeFuture.setHandler(compositeResult -> {
      if(compositeResult.failed()) {
        context.response()
          .setStatusCode(500)
          .end("Server error");
          logger.error("Composite future failed on POSTs: " + compositeResult.cause().getLocalizedMessage());
      } else {
        JsonObject masterResultRecord = new JsonObject();
        masterResultRecord.put("user", userFuture.result());
        masterResultRecord.put("credentials", credentialsFuture.result());
        masterResultRecord.put("permissions", permissionsFuture.result());
        context.response()
          .setStatusCode(201)
          .end(masterResultRecord.encode());
      }
    });
    
  }
  
  
  private Future<JsonObject> getRecordByQuery(String field, String value, String resultKey, String tenant, String moduleURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    String queryString = "?query=" + field + "=" + value;
    logger.debug("Requesting record from mod-users with query '" + queryString + "'");
    HttpClientRequest request = httpClient.getAbs(moduleURL + queryString);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json")
            .handler(queryResult -> {
              if(queryResult.statusCode() != 200) {
                queryResult.bodyHandler(body -> {
                  future.fail("Got status code "+ queryResult.statusCode() + ": " + body.toString());
                });
              } else {
                logger.debug("Got status code " + queryResult.statusCode() + " - Calling bodyhandler to parse results");
                queryResult.bodyHandler(body -> {
                  JsonObject result;
                  logger.debug("Got body: " + body.toString());
                  try {
                    result = new JsonObject(body.toString());
                  } catch(Exception e) {
                    future.fail("Unable to parse body as JSON: " + e.getLocalizedMessage());
                    return;
                  }
                  Integer totalRecords = result.getInteger("total_records");
                  if(totalRecords == null || totalRecords < 1) {
                    future.fail(new RecordNotFoundException("No record found for query '" + queryString + "'"));
                  } else if(totalRecords > 1) {
                    future.fail(new ResultNotUniqueException("'" + queryString + "' returns multiple results"));
                  } else {
                    JsonObject record = result.getJsonArray(resultKey).getJsonObject(0);
                    logger.debug("Got record " + record.encode());
                    future.complete(record);
                  }
                });
              }
            })
            .exceptionHandler(exception -> {
              logger.debug("Something bad, wtf: " + exception.getLocalizedMessage());
              future.fail(exception);
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
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json")
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

  private Future<JsonObject> putRecordByKey(String key, JsonObject entity, String tenant, String moduleURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    HttpClientRequest request = httpClient.putAbs(moduleURL + "/" + key);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .putHeader("Accept", "application/json, text/plain")
            .putHeader("Content-Type", "application/json");
    request.handler(queryResult -> {
      if(queryResult.statusCode() != 200 && queryResult.statusCode() != 204) {
        queryResult.bodyHandler(body -> {
          future.fail("Got status code " + queryResult.statusCode() + " from URL '" + moduleURL + "/" + key + "': " + body.toString());
        });
      } else {
        queryResult.bodyHandler(body -> {
          if(body.length() > 0) {
            JsonObject result;
            try {
              result = new JsonObject(body.toString());
            } catch(Exception e) {
              future.fail("Unable to parse body (" + body.toString() + ") as JSON: " + e.getLocalizedMessage());
              return;
            }
            future.complete(result);
          } else {
            future.complete(entity);
          }
        });
      }
    })
    .end(entity.encode());
    return future;
  }

  private Future<JsonObject> postRecord(JsonObject entity, String tenant, String moduleURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    HttpClientRequest request = httpClient.postAbs(moduleURL);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json");
    request.handler(queryResult -> {
      if(queryResult.statusCode() != 201) {
        queryResult.bodyHandler(body -> {
          future.fail("Got status code " + queryResult.statusCode() + ": " + body.toString());
        });
      } else {
        queryResult.bodyHandler(body -> {
          JsonObject result;
          try {
            result = new JsonObject(body.toString());
          } catch(Exception e) {
            future.fail("Unable to parse body (" + body.toString() + ") as JSON: " + e.getLocalizedMessage());
            return;
          }
          future.complete(result);
        });
      }
    })
    .end(entity.encode());
    return future;
  }
  
  private Future<JsonObject> getPermissionsRecord(String username, String tenant, String okapiURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    getRecordByKey(username, tenant, okapiURL + "/perms/users", requestToken).setHandler(getResponse -> {
      if(getResponse.failed()) {
        if(getResponse.cause() instanceof RecordNotFoundException) {
          future.complete(null);
        } else {
          future.fail(getResponse.cause());
        }
      } else {
        future.complete(getResponse.result());
      }
    });
    return future;
  }
  
  private Future<JsonObject> getCredentialsRecord(String username, String tenant, String okapiURL, String requestToken) {
    Future<JsonObject> future = Future.future();
    getRecordByKey(username, tenant, okapiURL + "/authn/credentials", requestToken).setHandler(getResponse -> {
      if(getResponse.failed()) {
        if(getResponse.cause() instanceof RecordNotFoundException) {
          future.complete(null);
        } else {
          future.fail(getResponse.cause());
        }
      } else {
        future.complete(getResponse.result());
      }
    });
    return future;
  }

  private Future<JsonObject> putUserRecord(String id, JsonObject record, String tenant, String okapiURL, String requestToken) {
    return putRecordByKey(id, record, tenant, okapiURL + "/users", requestToken);
  }

  private Future<JsonObject> putPermissionsRecord(String username, JsonObject record, String tenant, String okapiURL, String requestToken) {
    return putRecordByKey(username, record, tenant, okapiURL + "/perms/users", requestToken);
  }
  
  private Future<JsonObject> putCredentialsRecord(String username, JsonObject record, String tenant, String okapiURL, String requestToken) {
    return putRecordByKey(username, record, tenant, okapiURL + "/authn/credentials", requestToken);
  }

  private Future<JsonObject> postUserRecord(JsonObject record, String tenant, String okapiURL, String requestToken) {
    return postRecord(record, tenant, okapiURL + "/users", requestToken);
  }
  
  private Future<JsonObject> postPermissionsRecord(JsonObject record, String tenant, String okapiURL, String requestToken) {
    return postRecord(record, tenant, okapiURL + "/perms/users", requestToken);
  }
    
  private Future<JsonObject> postCredentialsRecord(JsonObject record, String tenant, String okapiURL, String requestToken) {
    return postRecord(record, tenant, okapiURL + "/authn/credentials", requestToken);
  }
  
}
