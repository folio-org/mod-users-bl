package org.folio.bl.users;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.folio.bl.util.RecordNotFoundException;
import org.folio.bl.util.ResultNotUniqueException;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;


/**
 *
 * @author kurt
 */


public class MainVerticle extends AbstractVerticle {
   
  private static String OKAPI_URL_HEADER = "X-Okapi-URL";
  private static String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  private static String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  private static String OKAPI_PERMISSIONS_HEADER = "X-Okapi-Permissions";
  
  private String dummyOkapiURL = null;
  private static String URL_ROOT = "/bl-users";
  private static String READ_PERMISSION = "users-bl.item.get";
  
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
    
    router.post().handler(BodyHandler.create());
    router.get(URL_ROOT + "/by-id/:id").handler(this::handleIdRetrieve);
    router.get(URL_ROOT + "/by-username/:username").handler(this::handleUsernameRetrieve);
    router.get(URL_ROOT + "/_self").handler(this::handleSelfRetrieve);
    router.post(URL_ROOT + "/login").handler(this::handleLogin);
    router.put().handler(BodyHandler.create());
    //router.put(URL_ROOT + "/by-id/:id").handler(this::handleModify);
    //router.put(URL_ROOT + "/:username").handler(this::handleModify);
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

  private String getOkapiURL(RoutingContext context) {
    if(dummyOkapiURL == null) {
      return context.request().getHeader(OKAPI_URL_HEADER);
    }
    return dummyOkapiURL;
  }
  private void handleLogin(RoutingContext context) {
		String okapiURL = getOkapiURL(context);
		String tenant = context.request().getHeader(OKAPI_TENANT_HEADER);
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);
		JsonObject credentialsObject = context.getBodyAsJson();
		if(credentialsObject == null || !credentialsObject.containsKey("username") || !credentialsObject.containsKey("password")) {
			context.response().setStatusCode(400)
				.end("Improperly formatted request");
		} else {
			Future<String> JWTFuture = getLoginToken(credentialsObject.getString("username"), credentialsObject.getString("password"), okapiURL + "/authn/login", token, tenant);
			JWTFuture.setHandler( res -> {
				if(res.failed()) {
					String[] parts = splitErrorMessage(res.cause().getLocalizedMessage());
					int returnCode = Integer.parseInt(parts[0]);
					String message = parts[1];
					logger.debug("Login request failed. Got status " + returnCode + ", " + message);
					context.response().setStatusCode(returnCode)
						.end(message);
				} else {
					String jwt = res.result();
				  CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
					headers.add(OKAPI_TOKEN_HEADER, jwt);
					handleRetrieve(context, credentialsObject.getString("username"), false, true, 201, headers);
				}
			});
		}
	}

  private void handleSelfRetrieve(RoutingContext context) {
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);
    String username = getUsername(token);
    handleRetrieveSimple(context, username, true);
  }

  private void handleUsernameRetrieve(RoutingContext context) {
    String username = context.request().getParam("username");
    handleRetrieveSimple(context, username, false);
  }

  private void handleIdRetrieve(RoutingContext context) {
    String id = context.request().getParam("id");
    String okapiURL = getOkapiURL(context);
    String tenant = context.request().getHeader(OKAPI_TENANT_HEADER);
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);
    String permissions = context.request().getHeader(OKAPI_PERMISSIONS_HEADER);
    Future<JsonObject> userRecordFuture = getRecordByKey(id, tenant, okapiURL + "/users", token);
    userRecordFuture.setHandler(userRecordResult -> {
      if(userRecordResult.failed()) {
        if(userRecordResult.cause() instanceof RecordNotFoundException) {
          context.response()
            .setStatusCode(404)
            .end("Unable to locate user with id '" + id + "'");
        } else {
          String message = "Error retrieving user record: " + userRecordResult.cause().getLocalizedMessage();
          context.response()
            .setStatusCode(500)
            .end(message);
          logger.error(message);
        }
      } else {
        handleRetrieveSimple(context, userRecordResult.result().getString("username"), false);
      }
    });
  }

 	private void handleRetrieveSimple(RoutingContext context, String username, boolean selfOnly) {
		handleRetrieve(context, username, selfOnly, false, 200, null);
	}

  private void handleRetrieve(RoutingContext context, String username, boolean selfOnly, boolean expandPerms, int returnCode, CaseInsensitiveHeaders headers) {
    String okapiURL = getOkapiURL(context);
    String tenant = context.request().getHeader(OKAPI_TENANT_HEADER);
    String token = context.request().getHeader(OKAPI_TOKEN_HEADER);
    String permissions = context.request().getHeader(OKAPI_PERMISSIONS_HEADER);

    Future<JsonObject> userRecordFuture;
    userRecordFuture = getRecordByQuery("username", username, "users", tenant, okapiURL + "/users", token);
    
    userRecordFuture.setHandler(userRecordResult -> {
      if(userRecordResult.failed()) {
        if(userRecordResult.cause() instanceof RecordNotFoundException) {
          context.response().setStatusCode(404);
          context.response().end("Unable to find user '" + username + "'");
        } else if(userRecordResult.cause() instanceof ResultNotUniqueException) {
          context.response()
                  .setStatusCode(400)
                  .end("Username '" + username + "' is not unique");
        } else {
          String message = "Error retrieving user record: " + userRecordResult.cause().getLocalizedMessage();
          context.response()
                  .setStatusCode(500)
                  .end(message);
          logger.error(message);
        }
      } else {
        JsonObject masterResponseObject = new JsonObject();
        String retrievedUsername = userRecordResult.result().getString("username");

        //Future<JsonObject> credentialsObjectFuture;
        Future<JsonObject> permissionsObjectFuture;
				Future<JsonObject> userPermsFuture;
				Future<JsonObject> groupObjectFuture;
        
        //only retrieve credentials if we have full permissions (e.g. selfOnly == false)
				/*
        if(selfOnly) {
          credentialsObjectFuture = Future.succeededFuture(new JsonObject());
        } else {
          credentialsObjectFuture = this.getCredentialsRecord(retrievedUsername, tenant, okapiURL, token);
        }
				*/
        permissionsObjectFuture = this.getPermissionsRecord(retrievedUsername, tenant, okapiURL, token);
				if(userRecordResult.result().getString("patronGroup") != null) {
					groupObjectFuture = getRecordByKey(userRecordResult.result().getString("patronGroup"), tenant, okapiURL + "/groups", token);
				} else {
					groupObjectFuture = Future.succeededFuture(new JsonObject());
				}
       
        Map<String, Future> futureMap = new HashMap<>();
        //futureMap.put("Login Module", credentialsObjectFuture);
        futureMap.put("Permissions Module", permissionsObjectFuture);
				futureMap.put("Groups Module", groupObjectFuture);
        if(expandPerms) {
					userPermsFuture = getUserPermissions(retrievedUsername, okapiURL, tenant, token, true, true);
					futureMap.put("User Perms", userPermsFuture);
				} else {
					userPermsFuture = null;
				}
        logger.debug("Creating composite future");
        CompositeFuture compositeFuture = CompositeFuture.join(new ArrayList(futureMap.values()));
        JsonObject userRecordObject = userRecordResult.result();
        compositeFuture.setHandler(compositeResult -> {          
					masterResponseObject.put("userId", userRecordObject.getString("id"));
					masterResponseObject.put("permissionsId", userRecordObject.getString("username"));
					masterResponseObject.put("groupId", userRecordObject.getString("patronGroup"));
          masterResponseObject.put("user", userRecordObject);
          if(compositeResult.failed()) {
            Map<String, String> failMap = getCompositeFailureMap(getFailureMap(futureMap));
            logger.debug("Error resolving composite future: " + failMap.get("message"));
            context.response()
                    .putHeader("Content-Type", "text/plain")
                    .setStatusCode(Integer.parseInt(failMap.get("code")))
                    .end(failMap.get("message"));
           
          } else {
            //Future<JsonArray> userGroups = getGroupsForUser(userRecordObject.getString("id"), tenant, okapiURL, token);
          	masterResponseObject.put("permissions", permissionsObjectFuture.result());
						masterResponseObject.put("group", groupObjectFuture.result());
						if(expandPerms) {
							logger.debug("Expanded perms: " + userPermsFuture.result().encode());
							JsonArray expandedPermList = userPermsFuture.result().getJsonArray("permissionNames");
							if(!expandedPermList.isEmpty()) {
								masterResponseObject.getJsonObject("permissions").remove("permissions");
								masterResponseObject.getJsonObject("permissions").put("permissions", expandedPermList );
							}
						}
            
						context.response()
            	.putHeader("Content-Type", "application/json")
              .setStatusCode(returnCode);
						if(headers != null) {
								context.response().headers().addAll(headers);
						}
						context.response().end(masterResponseObject.encode());
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

        CompositeFuture compositeFuture = CompositeFuture.join(userFuture, credentialsFuture, permissionsFuture);
        compositeFuture.setHandler(compositeResult -> {
          if(compositeResult.failed()) {
            logger.error("Composite future failed: " + compositeResult.cause().getLocalizedMessage());
            Future[] completedFutureArray = { userFuture, credentialsFuture, permissionsFuture };
            Map<String, String> failMap = new HashMap<>();
            for( Future f : completedFutureArray ) {
              if(f.failed()) {
                String[] messageParts = splitErrorMessage(f.cause().getLocalizedMessage());
                failMap.put(messageParts[0], messageParts[1]);
              }
            }
            context.response()
              .setStatusCode(500)
              .end("Server error");              
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
  
  
	private Future<String> getLoginToken(String username, String password, String moduleURL, String requestToken, String tenant) {
		Future<String> future = Future.future();
		HttpClient httpClient = vertx.createHttpClient();
		logger.debug("Requesting login from " + moduleURL);
		HttpClientRequest request = httpClient.postAbs(moduleURL);
		request.putHeader(OKAPI_TENANT_HEADER, tenant)
			.putHeader("Accept", "application/json")
			.putHeader("Content-type", "application/json")
			.putHeader(OKAPI_TOKEN_HEADER, requestToken)
			.handler(queryResult -> {
				if(queryResult.statusCode() != 201) {
					queryResult.bodyHandler(body -> {
						future.fail(queryResult.statusCode() + "||" + body.toString());
					});
				} else {
					String token = queryResult.getHeader(OKAPI_TOKEN_HEADER);
					future.complete(token);
				}
			})
			.end("{ \"username\": " + " \"" + username + "\", \"password\": " + "\"" + password + "\" }");
		return future;
	}

	private Future<JsonObject> getUserPermissions(String username, String okapiURL, String tenant, String requestToken, boolean expanded, boolean full) {
		Future<JsonObject> future = Future.future();
		HttpClient httpClient = vertx.createHttpClient();
		String paramString = "?expanded=" + expanded + "&full=" + full;
		String requestURL = okapiURL  + "/perms/users/" + username + "/permissions" + paramString;
		logger.debug("Requesting user permissions with url '" + requestURL + "'");
		HttpClientRequest request = httpClient.getAbs(requestURL);
		request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
			.putHeader(OKAPI_TENANT_HEADER, tenant)
			.putHeader("Accept", "application/json")
			.putHeader("Content-Type", "application/json")
			.handler(queryResult -> {
				if(queryResult.statusCode() != 200) {
					queryResult.bodyHandler(body -> {
						future.fail(queryResult.statusCode() + "||" + body.toString());
					});
				} else {
					queryResult.bodyHandler(body -> {
						future.complete(new JsonObject(body.toString()));
					});
				}
			})
			.exceptionHandler(exception -> {
				future.fail(exception);
				logger.debug("Something bad happened: " + exception.getLocalizedMessage());
			})
			.end();
		return future;
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
                  future.fail(queryResult.statusCode() + "||" + body.toString());
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
                    future.fail(queryResult.statusCode() + "||" + body.toString());
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
          future.fail(queryResult.statusCode() + "||From URL '" + moduleURL + "/" + key + "': " + body.toString());
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
          future.fail(queryResult.statusCode() + "||" + body.toString());
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
  
  private String[] splitErrorMessage(String message) {
    String[] results = message.split("\\|\\|", 2);
    if(results.length != 2) {
      String[] newResults = { "500", message };
      return newResults;
    }
    return results;
  }
  
  private Map<String, Map<String, String>> getFailureMap(Map<String, Future> futureMap) {
    Map<String, Map<String, String>> failMap = new HashMap<>();
    for(String k : futureMap.keySet()) {
      Future f = futureMap.get(k);
      if(f.failed()) {
        String[] failResults = splitErrorMessage(f.cause().getLocalizedMessage());
        Map<String, String> resultsMap = new HashMap<>();
        resultsMap.put("code", failResults[0]);
        resultsMap.put("message", failResults[1]);
        failMap.put(k, resultsMap);
      }
    }
    return failMap;
  }
  
  private Map<String, String> getCompositeFailureMap(Map<String, Map<String, String>> failMap) {
    String highKey = null;
    int highError = 0;
    List<String> messageList = new ArrayList<>();
    Set<String> keySet = failMap.keySet();
    for(String key : keySet) {
      Map<String, String> resultsMap = failMap.get(key);
      messageList.add(key + ": " + resultsMap.get("message"));
      int keyValue = Integer.parseInt(resultsMap.get("code"));
      if(keyValue > highError) {
        highError = keyValue;
        highKey = key;
      }
    }
    String valueComposite = StringUtils.join(messageList, ",");
    Map<String, String> result = new HashMap<>();
    if(highError > 0) {
      result.put("code", Integer.toString(highError));
      result.put("message", valueComposite);
    } else {
      result.put("code", "500");
      result.put("message", "Error getting output from backend modules");
    }
    return result;
  }
  
  private JsonObject parseTokenPayload(String token) {
    String[] tokenParts = token.split("\\.");
    if(tokenParts.length == 3) {
      String encodedPayload = tokenParts[1];
      byte[] decodedJsonBytes = Base64.getDecoder().decode(encodedPayload);
      String decodedJson = new String(decodedJsonBytes);
      return new JsonObject(decodedJson);
    } else {
      return null;
    }
  }

  private String getUsername(String token) {
    JsonObject payload = parseTokenPayload(token);
    if(payload == null) { return null; }
    String username = payload.getString("sub");
    return username;
  }

  private boolean allowAccessByNameorPermission(String permissions, String permissionName, String token, String username) {
   if(allowAccessByName(token, username)) {
     return true;
   }
    return allowAccessByPermission(permissions, permissionName);
  }

  private boolean allowAccessByPermission(String permissions, String permissionName) {
    logger.debug("Checking for permission '" + permissionName + "' in array encoded by '" + permissions + "'");
    if(permissions == null || permissions.isEmpty()) {
      return false;
    }
    JsonArray permissionsArray = new JsonArray(permissions);
    if(permissionsArray != null && permissionsArray.contains(permissionName)) {
      logger.debug("Permission allowed for possessing permission bit '" + permissionName + "'");
      return true;
    }
    return false;
  }
  
  private boolean allowAccessByName(String token, String username) {
    String tokenUsername = getUsername(token);
    if(tokenUsername.equals(username)) {
      logger.debug("Permission allowed for own username (" + username + ")");
      return true;
    }
    return false;
  }
  
  private Future<JsonArray> getGroupsForUser(String userId, String tenant, String okapiURL, String requestToken) {
    Future<JsonArray> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    String requestURL = okapiURL + "/groups";
    HttpClientRequest request = httpClient.getAbs(requestURL);
    request.putHeader(OKAPI_TOKEN_HEADER, requestToken)
            .putHeader(OKAPI_TENANT_HEADER, tenant)
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json");
    request.handler(queryResult -> {
      if(queryResult.statusCode() != 200) {
        queryResult.bodyHandler(body-> {
          future.fail(queryResult.statusCode() + "||" + body.toString());
        });
      } else {
        queryResult.bodyHandler(body -> {
          JsonObject groupListObject = new JsonObject(body.toString());
          JsonArray groups = groupListObject.getJsonArray("usergroups");
          List<Future> futureList = new ArrayList<>();
          for(Object group : groups) {
            String groupId = ((JsonObject)group).getString("_id");
            Future<String> memberFuture = getUserInGroup(groupId, userId, okapiURL, tenant, requestToken);
            futureList.add(memberFuture);
          }
          logger.debug("Creating composite future to get results from groups list (" + futureList.size() + " elements)");
          CompositeFuture compositeFuture = CompositeFuture.join(futureList);
          compositeFuture.setHandler(compositeResult -> {
            if(compositeFuture.failed()) {
              logger.debug("Group retrieval failed: " + compositeFuture.cause().getLocalizedMessage());
              future.complete(new JsonArray());
            } else {
              JsonArray result = new JsonArray();
              for(Future completedFuture : futureList) {
                String groupString = ((Future<String>)completedFuture).result();
                if(groupString != null) {
                  result.add(groupString);
                }
              }
              future.complete(result);
            }
          });
        });
      }
    });
    request.end();
    logger.debug("Getting group list from " + requestURL);
    
    return future;        
  }

  private Future<JsonObject> getGroups(String okapiURL, String tenant, String requestToken) {
    Future<JsonObject> future = Future.future();
    HttpClient httpClient = vertx.createHttpClient();
    String groupRequestURL = okapiURL + "/groups";
    HttpClientRequest groupRequest = httpClient.getAbs(groupRequestURL);
    groupRequest.putHeader(OKAPI_TOKEN_HEADER, requestToken)
      .putHeader(OKAPI_TENANT_HEADER, tenant)
      .putHeader("Accept", "application/json")
      .putHeader("Content-Type", "application/json");
    groupRequest.handler(groupResult -> {
      if(groupResult.statusCode() != 200) {
        groupResult.bodyHandler(groupBody -> {
          String message = "Error retrieving groups: " + groupBody.toString();
          logger.debug(message);
          future.fail(message);
        });
      } else {
        groupResult.bodyHandler(groupBody -> {
          future.complete(new JsonObject(groupBody.toString()));
        });
      }
    });
    groupRequest.end();
    return future;
  }

  private Future<String> getUserInGroup (String groupId, String userId, String okapiURL, String tenant, String requestToken) {
    Future<String> thisFuture = Future.future();
    HttpClient httpGroupClient = vertx.createHttpClient();
    String usersRequestURL = okapiURL + "/groups/" + groupId + "/users";
    HttpClientRequest groupRequest = httpGroupClient.getAbs(usersRequestURL);
    groupRequest.putHeader(OKAPI_TOKEN_HEADER, requestToken)
        .putHeader(OKAPI_TENANT_HEADER, tenant)
        .putHeader("Accept", "application/json")
        .putHeader("Content-Type", "application/json");
    groupRequest.handler(usersQueryResult -> {
      if(usersQueryResult.statusCode() != 200) {
        logger.debug("Error retrieving users. Got status " + usersQueryResult.statusCode());
        thisFuture.complete(null);
      } else {
        usersQueryResult.bodyHandler(usersBody -> {
          JsonObject usersListObject = new JsonObject(usersBody.toString());
          JsonArray users = usersListObject.getJsonArray("users");
          for(Object user : users) {
            if(((JsonObject)user).getString("id").equals(userId)) {
              thisFuture.complete(groupId);
              return;
            }
          }
          thisFuture.complete(null);
        });
      }
    });
    groupRequest.end();
    logger.debug("Getting group users list from " + usersRequestURL);
    return thisFuture;
  }
  
}
