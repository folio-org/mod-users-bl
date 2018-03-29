package org.folio.rest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.ArrayList;
import java.util.HashMap;
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
  private JsonStore permsUsersStore;
  private JsonStore permsPermissionsStore;
  private JsonStore groupsStore;

  @Override
  public void start(Future<Void> future) {
    final int port = context.config().getInteger("http.port");
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
    permsUsersStore = new JsonStore();
    groupsStore = new JsonStore();
  }

  private void handleRequest(RoutingContext context) {
    MockResponse mockResponse = null;
    if(context.request().uri().startsWith("/users")) {
      mockResponse = handleUsers(context.request().rawMethod(),
              context.request().uri(), context.getBodyAsString(), context);
    } else if(context.request().uri().startsWith("/perms/users")) {
      mockResponse = handlePermUsers(context.request().rawMethod(),
              context.request().uri(), context.getBodyAsString(), context);
    } else if(context.request().uri().startsWith("/groups")) {
      mockResponse = handleGroups(context.request().rawMethod(),
              context.getBodyAsString(), context);
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
    int code = 200;
    String response = null;
    if(verb.equals("GET")) {
      String id = context.pathParam("id");
      if(id == null) {
        //Get list of perm users
        List<JsonObject> userList = getCollectionWithContextParams(permsUsersStore, context, null);
        JsonObject responseObject = wrapCollection(userList, "permissionUsers");
        response = responseObject.encode();
      } else {
        if(!url.endsWith("permissions")) {
          //Get a single perm user
          JsonObject item = permsUsersStore.getItem(id);
          if(item == null) {
            code = 404;
            response = "Not found";
          } else {
            response = item.encode();
          }
        } else {
          //Get a single perm user's permissions
          JsonObject item = permsUsersStore.getItem(id);
          String full = context.pathParam("full");
          String expanded = context.pathParam("expanded");
          JsonArray permissions = item.getJsonArray("permissions");
          JsonObject permNameListObject = new JsonObject();
          code = 200;
          if(!expanded.equals("true") && !full.equals("true")) {
            permNameListObject.put("permissionName", permissions);
            permNameListObject.put("totalRecords", permissions.size());
          } else if(!expanded.equals("true")) {
            //full only
            makeFullPerms(permissions, permsPermissionsStore);
            permNameListObject.put("permissionName", permissions);
            permNameListObject.put("totalRecords", permissions.size());
          } else if(!full.equals("true")) {
            //expanded only
            JsonArray expandedPerms = recursePermList(permissions, permsPermissionsStore);
            permNameListObject.put("permissionName", expandedPerms);
            permNameListObject.put("totalRecords", expandedPerms.size());
          } else {
            //full and expanded
            JsonArray expandedPerms = recursePermList(permissions, permsPermissionsStore);
            makeFullPerms(expandedPerms, permsPermissionsStore);
            permNameListObject.put("permissionName", expandedPerms);
            permNameListObject.put("totalRecords", expandedPerms.size());
          }
          response = permNameListObject.encode();
        }
      }
    } else if(verb.equals("POST")) {
      //Create a new perm user
      JsonObject ob = permsUsersStore.addItem(null, new JsonObject(payload));
      if(ob == null) {
        code = 422;
        response = "Unable to add object";
      } else {
        code = 201;
        response = ob.encode();
      }
    } else if(verb.equals("PUT")) {
      String id = context.pathParam("id");
      if(id == null) {
        code = 400;
        response = "No identifier provided";
      } else {
        //modify perm user
        boolean success = permsUsersStore.updateItem(id, new JsonObject(payload));
        if(success) {
          code = 204;
          response = "";
        } else {
          code = 404;
          response = "Not found";
        }
      }
    } else if(verb.equals("DELETE")) {
      String id = context.pathParam("id");
      if(id == null) {
        //Error, no id
        code = 400;
        response = "No identifier provided";
      } else {
        //delete perm user
        boolean success = permsUsersStore.deleteItem(id);
        if(success) {
          code = 204;
          response = "";
        } else {
          code = 404;
          response = "Not found";
        }
      }
    }
    if(response == null) {
      code = 500;
      response = "Server error";
    }
    return new MockResponse(code, response);
  }

  private MockResponse handleGroups(String verb, String payload, RoutingContext context) {
    int code = 200;
    String response = "";
    switch (verb.toUpperCase()) {
    case "GET":
      List<JsonObject> responseList = getCollectionWithContextParams(groupsStore, context, null);
      JsonObject responseObject = wrapCollection(responseList, "groups");
      response = responseObject.encode();
      break;
    case "PUT":
      {
        String groupId = context.pathParam("groupId");
        boolean success = groupsStore.updateItem(groupId, new JsonObject(payload));
        if(success) {
          code = 204;
          response = "";
        } else {
          code = 404;
          response = "Not found";
        }
      }
      break;
    case "POST":
      JsonObject ob = groupsStore.addItem(null, new JsonObject(payload));
      if(ob == null) {
        code = 422;
        response = "Unable to add object";
      } else {
        code = 201;
        response = ob.encode();
      }
      break;
    case "DELETE":
      {
        String groupId = context.pathParam("groupId");
        boolean ok = groupsStore.deleteItem(groupId);
        if (ok) {
          code = 204;
          response = "";
        } else {
          code = 404;
          response = "Not found";
        }
      }
      break;
    default:
      code = 500;
      response = "Request type not supported: " + verb;
    }
    return new MockResponse(code, response);
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

  private JsonObject getPermObject(String permName, JsonStore permStore) {
    Map<String, String> getBy = new HashMap();
    getBy.put("permissionName", permName);
    List<JsonObject> obList = permStore.getCollection(0, 1, getBy);
    if(obList.isEmpty()) {
      return null;
    }
    return obList.get(0);
  }

  private void makeFullPerms(JsonArray permList, JsonStore permStore) {
    List<Object> deleteList = new ArrayList<>();
    List<Object> addList = new ArrayList<>();
    for(Object ob : permList) {
      if(ob instanceof String) {
        JsonObject expandedPerm = getPermObject((String)ob, permStore);
        deleteList.add(ob);
        addList.add(expandedPerm);
      }
    }
    for(Object ob : deleteList) { permList.remove(ob); }
    for(Object ob : addList) { permList.add(ob); }
  }

  private JsonArray recursePermList(JsonArray permList, JsonStore permStore) {
    JsonArray newList = new JsonArray();
    for(Object ob : permList) {
      if(!(ob instanceof String)) {
        continue;
      }
      newList.add((String) ob);
      JsonObject permOb = getPermObject((String)ob, permStore);
      if(permOb == null) { continue; }
      JsonArray subPerms = permOb.getJsonArray("subPermissions");
      if(subPerms == null) { continue; }
      JsonArray expandedSubPerms = recursePermList(subPerms, permStore);
      for(Object perm : expandedSubPerms) {
        if(!newList.contains(perm)) {
          newList.add(perm);
        }
      }
    }
    return newList;
  }
}


