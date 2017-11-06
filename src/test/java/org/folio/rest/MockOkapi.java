/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author kurt
 */
public class MockOkapi extends AbstractVerticle {
  private JsonStore userStore;
  
  public void start(Future<Void> future) {
    final int port = context.config().getInteger("port");
    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();
    
    router.route("/*").handler(BodyHandler.create());
    router.route("/*").handler(this::handleRequest);
    System.out.println("Running MockOkapi on port " + port);
    server.requestHandler(router::accept).listen(port, result -> {
      if(result.failed()) {
        future.fail(result.cause());
      }
      else {
        future.complete();
      }
    });
    
  }
  
  public MockOkapi() {
    userStore = new JsonStore();
  }
  
  private void handleRequest(RoutingContext context) {
    MockResponse mockResponse = null;
    if(context.request().uri().startsWith("/users")) {
      mockResponse = handleUsers(context.request().rawMethod(), context.request().uri(), context.getBodyAsString(), context);
    } else {
      context.response()
              .setStatusCode(400)
              .end("Endpoint " + context.request().uri() + " not supported");
    }
    if(mockResponse != null) {
      context.response()
              .setStatusCode(mockResponse.getCode())
              .end(mockResponse.getContent());
    }
  }
  
  private MockResponse handleUsers(String verb, String url, String payload, RoutingContext context) {
    int code = 200;
    String response = "";
    if(verb.toUpperCase().equals("GET")) {
      String userId = context.pathParam("userId");
      if(userId != null) {
        JsonObject item = userStore.getItem(userId);
        if(item == null) {
          code = 404;
          response = "Not found";
        } else {
          code = 200;
          response = item.encode();
        }
      } else {
        List<JsonObject> responseList = getCollectionWithContextParams(userStore, context, null);
        JsonObject responseObject = wrapCollection(responseList, "users");
        response = responseObject.encode();
      }      
    } else if(verb.toUpperCase().equals("PUT")) {
      String userId = context.pathParam("userId");
      boolean success = userStore.updateItem(userId, new JsonObject(payload));
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    } else if(verb.toUpperCase().equals("POST")) {
      JsonObject ob = userStore.addItem(null, new JsonObject(payload));
      if(ob == null) {
        code = 422;
        response = "Unable to add object";
      } else {
        code = 201;
        response = ob.encode();
      }
    } else if(verb.toUpperCase().equals("DELETE")) {
      String userId = context.pathParam("userId");
      boolean success = userStore.deleteItem(userId);
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    }
    return new MockResponse(code, response);
  }
  
  private MockResponse handlePermUsers(String verb, String url, String payload,
          RoutingContext context) {
    verb = verb.toUpperCase();
    if(verb.equals("GET")) {
      String id = context.pathParam("id");
      if(id == null) {
        //Get list of perm users
      } else {
        if(!url.endsWith("permissions")) {
          //Get a single perm user
        } else {
          //Get a single perm user's permissions
        }
      }
    } else if(verb.equals("POST")) {
      //Create a new perm user
    } else if(verb.equals("PUT")) {
      String id = context.pathParam("id");
      if(id == null) {
        //Error, no id
      } else {
        //Modify a user
      }
    } else if(verb.equals("DELETE")) {
      String id = context.pathParam("id");
      if(id == null) {
        //Error, no id
      } else {
        //Modify a user
      }
    }
  }
  
  private JsonObject wrapCollection(List<JsonObject> obList, String collectionName) {
    JsonObject result = new JsonObject();
    JsonArray obArray = new JsonArray();
    for(JsonObject ob : obList) {
      obArray.add(ob);
    }
    result.put(collectionName, obArray);
    result.put("totalRecords", obList.size() );
    return result;
  }
  
  List<JsonObject> getCollectionWithContextParams(JsonStore jsonStore, RoutingContext context, Map<String, String> filterMap) {
    List<JsonObject> result;
    int offset = Integer.parseInt(context.pathParams().getOrDefault("offset", "0"));
    int limit = Integer.parseInt(context.pathParams().getOrDefault("limit", "30"));
    return jsonStore.getCollection(offset, limit, filterMap);
  }
}
