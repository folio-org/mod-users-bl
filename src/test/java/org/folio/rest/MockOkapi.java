package org.folio.rest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.UpdateCredentials;
import org.folio.util.StringUtil;
import org.z3950.zing.cql.CQLParseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.folio.rest.BLUsersAPITest.USER_TEST_ID;
import static org.folio.rest.BLUsersAPITest.USER_TEST_NAME;

/**
 * @author kurt
 */
public class MockOkapi extends AbstractVerticle {
  private JsonStore userStore;
  private JsonStore userTenants;
  private JsonStore permsUsersStore;
  private JsonStore permsPermissionsStore;
  private JsonStore groupsStore;
  private JsonStore proxiesStore;
  private JsonStore servicePointsStore;
  private JsonStore servicePointsUsersStore;
  private JsonStore configurationStore;
  private JsonStore resetPasswordActionStore;
  private JsonStore loansStorage;
  private JsonStore requestsStorage;
  private JsonStore accountsStorage;
  private JsonStore manualBlocksStorage;

  private static final String USERS_ENDPOINT = "/users";
  private static final String USER_TENANT_ENDPOINT = "/user-tenants";
  private static final String PASSWORD_VALIDATE_ENDPOINT = "/password/validate";
  private static final String PASSWORD_UPDATE_ENDPOINT = "/authn/update";
  private static final String PERMS_USERS_ENDPOINT = "/perms/users";
  private static final String PERMS_PERMISSIONS_ENDPOINT = "/perms/permissions";
  private static final String GROUPS_ENDPOINT = "/groups";
  private static final String PROXIES_ENDPOINT = "/proxiesfor";
  private static final String SERVICE_POINTS_ENDPOINT = "/service-points";
  private static final String SERVICE_POINTS_USERS_ENDPOINT = "/service-points-users";
  static final String CONFIGURATIONS_ENTRIES_ENDPOINT = "/configurations/entries";
  private static final String PASSWORD_RESET_ACTION_ENDPOINT = "/authn/password-reset-action";
  private static final String SIGN_TOKEN_ENDPOINT = "/token";
  private static final String RESET_PASSWORD_ENDPOINT = "/authn/reset-password";
  private static final String NOTIFY_ENDPOINT = "/notify";
  private static final String LOANS_ENDPOINT = "/loan-storage/loans";
  private static final String REQUESTS_ENDPOINT = "/request-storage/request";
  private static final String ACCOUNTS_ENDPOINT = "/accounts";
  private static final String MANUAL_BLOCKS_ENDPOINT = "/manualblocks";

  @Override
  public void start(Promise<Void> future) {
    final int port = context.config().getInteger("http.port");
    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();

    router.route("/*").handler(BodyHandler.create());
    router.route("/*").handler(this::handleRequest);
    System.out.println("Running MockOkapi on port " + port);
    server.requestHandler(router).listen(port, result -> {
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
    userTenants = new JsonStore();
    permsUsersStore = new JsonStore();
    permsPermissionsStore = new JsonStore();
    groupsStore = new JsonStore();
    proxiesStore = new JsonStore();
    servicePointsStore = new JsonStore();
    servicePointsUsersStore = new JsonStore();
    configurationStore = new JsonStore();
    resetPasswordActionStore = new JsonStore();
    loansStorage = new JsonStore();
    requestsStorage = new JsonStore();
    accountsStorage = new JsonStore();
    manualBlocksStorage = new JsonStore();
  }

  private void handleRequest(RoutingContext context) {
    MockResponse mockResponse = null;

    String[] endpoints = {USERS_ENDPOINT, USER_TENANT_ENDPOINT, PERMS_USERS_ENDPOINT, PASSWORD_VALIDATE_ENDPOINT, PASSWORD_UPDATE_ENDPOINT,
      PERMS_PERMISSIONS_ENDPOINT, GROUPS_ENDPOINT, PROXIES_ENDPOINT, SERVICE_POINTS_USERS_ENDPOINT,
      SERVICE_POINTS_ENDPOINT, CONFIGURATIONS_ENTRIES_ENDPOINT, PASSWORD_RESET_ACTION_ENDPOINT, SIGN_TOKEN_ENDPOINT,
      RESET_PASSWORD_ENDPOINT, NOTIFY_ENDPOINT, LOANS_ENDPOINT, REQUESTS_ENDPOINT, ACCOUNTS_ENDPOINT, MANUAL_BLOCKS_ENDPOINT};
    String uri = context.request().path();
    Matcher matcher;
    String id = null;
    String remainder = null;
    String activeEndpoint = null;
    HttpMethod method = context.request().method();
    for(String endpoint : endpoints) {
      if(uri.startsWith(endpoint)) {
        matcher = parseMockUri(uri, endpoint);
        if(matcher.matches()) {
          if(matcher.group(1) != null) {
            id = matcher.group(1);
            System.out.println("Found id " + id + "\n");
          }
          if(matcher.group(2) != null) {
            remainder = matcher.group(2);
            System.out.println("Remainder of url is: " + remainder + "\n");
          }
        } else {
          System.out.println(String.format(
             "No matching input found for uri %s and endpoint %s", uri, endpoint));
        }
        activeEndpoint = endpoint;
        break;
      } else {
        continue;
      }
    }
    if(activeEndpoint == null) {
      context.fail(new Error(String.format("Unable to find a matching endpoint for uri %s", uri)));
    }
    try {
      switch(activeEndpoint) {
        case USERS_ENDPOINT:
          mockResponse = handleUsers(method, id, remainder,
                  context.getBodyAsString(), context);
          break;
        case PERMS_USERS_ENDPOINT:
          mockResponse = handlePermsUsers(method, id, remainder,
                  context.getBodyAsString(), context);
          break;
        case PERMS_PERMISSIONS_ENDPOINT:
          mockResponse = handlePermsPermissions(method, id, remainder,
                  context.getBodyAsString(), context);
          break;
        case GROUPS_ENDPOINT:
          mockResponse = handleGroups(method, id, remainder,
                  context.getBodyAsString(), context);
          break;
        case PROXIES_ENDPOINT:
          mockResponse = handleProxies(method, id, remainder,
                  context.getBodyAsString(), context);
          break;
        case SERVICE_POINTS_ENDPOINT:
          mockResponse = handleServicePoints(method, id, remainder,
              context.getBodyAsString(), context);
          break;
        case SERVICE_POINTS_USERS_ENDPOINT:
          mockResponse = handleServicePointsUsers(method, id, remainder,
              context.getBodyAsString(), context);
          break;
        case CONFIGURATIONS_ENTRIES_ENDPOINT:
          mockResponse = handlerConfigurationEntries(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case PASSWORD_VALIDATE_ENDPOINT:
          mockResponse = handlerPasswordValidate(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case PASSWORD_UPDATE_ENDPOINT:
          mockResponse = handlerPasswordUpdate(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case PASSWORD_RESET_ACTION_ENDPOINT:
          mockResponse = handleResetPasswordActions(method, id, remainder, context.getBodyAsString(), context);
          break;
        case SIGN_TOKEN_ENDPOINT:
          mockResponse = handleSingToken(method, context.getBodyAsString());
          break;
        case RESET_PASSWORD_ENDPOINT:
          mockResponse = handleResetPassword(method, context.getBodyAsString());
          break;
        case NOTIFY_ENDPOINT:
          mockResponse = handleNotify(method, context.getBodyAsString());
          break;
        case LOANS_ENDPOINT:
          mockResponse = handleLoans(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case REQUESTS_ENDPOINT:
          mockResponse = handleRequests(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case ACCOUNTS_ENDPOINT:
          mockResponse = handleAccounts(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case MANUAL_BLOCKS_ENDPOINT:
          mockResponse = handleManualBlocks(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        case USER_TENANT_ENDPOINT:
          mockResponse = handleUserTenants(method, id, remainder,
            context.getBodyAsString(), context);
          break;
        default:
          break;
      }
    } catch(Exception e) {
      context.fail(e);
      return;
    }


    if(mockResponse != null) {
      System.out.println(String.format("Got mockResponse, code: %s, content: %s",
            mockResponse.getCode(), mockResponse.getContent()));

      context.response()
              .setStatusCode(mockResponse.getCode())
              .end(mockResponse.getContent());
    } else {
      context.response()
              .setStatusCode(400)
              .end("No such endpoint defined");
    }
  }

  private MockResponse handleUsers(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    int code = 200;
    String response = "";
    if(method == GET) {
      if(id != null) {
        JsonObject item = userStore.getItem(id);
        if(item == null) {
          code = 404;
          response = "Not found";
        } else {
          code = 200;
          response = item.encode();
        }
      } else {
        MockCollection responseCollection = getCollectionWithContextParams(userStore,
                context);
        JsonObject responseObject = wrapCollection(responseCollection, "users");
        response = responseObject.encode();
      }
    } else if(method == PUT) {
      boolean success = userStore.updateItem(id, new JsonObject(payload));
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    } else if(method == POST) {
      JsonObject ob = null;
      try {
        ob = userStore.addItem(null, new JsonObject(payload));
      } catch(Exception e) {
        code = 422;
        response = "Unable to add object: " + e.getLocalizedMessage();
      }
      if(ob != null) {
        code = 201;
        response = ob.encode();
      }
    } else if(method == DELETE) {
      boolean success = userStore.deleteItem(id);
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

  private MockResponse handleGroups(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(groupsStore, "usergroups", method, id, url, payload,
      context);
  }

  private MockResponse handleProxies(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(proxiesStore, "proxiesFor", method, id, url, payload,
            context);
  }

  private MockResponse handleUserTenants(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    int code = 200;
    String response = "";
    String userName = context.request().params().get("userName");
    if(USER_TEST_NAME.equals(userName)) {
      JsonObject result = new JsonObject();
      JsonArray userTenants = new JsonArray();
      JsonObject userTenant = new JsonObject();
      userTenant.put("id", UUID.randomUUID());
      userTenant.put("userId", USER_TEST_ID);
      userTenant.put("username", USER_TEST_NAME);
      userTenant.put("email", "user@gmail.org");
      userTenant.put("tenantId", "testTenant");

      userTenants.add(userTenant);
      result.put("userTenants", userTenants);
      result.put("totalRecords", 1);
      response = result.encode();
    } else {
      MockCollection responseCollection = getCollectionWithContextParams(userTenants,
        context);
      JsonObject responseObject = wrapCollection(responseCollection, "userTenants");
      response = responseObject.encode();
    }
    return new MockResponse(code, response);
  }

  private MockResponse handlePermsPermissions(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(permsPermissionsStore, "permissions", method, id, url,
            payload, context);
  }

  private MockResponse handleServicePoints(HttpMethod method, String id, String url,
      String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(servicePointsStore, "servicepoints", method, id, url,
        payload, context);
  }

  private MockResponse handleServicePointsUsers (HttpMethod method, String id, String url,
      String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(servicePointsUsersStore, "servicePointsUsers", method,
        id, url, payload, context);
  }

  private MockResponse handlerConfigurationEntries(HttpMethod method, String id, String url,
                                                   String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(configurationStore, "configs", method, id, url,
      payload, context);
  }

  private MockResponse handleResetPasswordActions(HttpMethod method, String id, String url,
                                                  String payload, RoutingContext context) throws CQLParseException {
    MockResponse response = handleBasicCrud(resetPasswordActionStore, "resetPasswordActions", method, id, url, payload, context);
    if (method.equals(HttpMethod.POST)) {
      return new MockResponse(HttpStatus.SC_CREATED, new JsonObject().put("passwordExists", true).encode());
    }
    return response;
  }

  private MockResponse handleSingToken(HttpMethod method, String payload) {
    if (method.equals(HttpMethod.POST)) {
      return new MockResponse(HttpStatus.SC_CREATED, "{\"token\":\"header.payload.signature\"}");
    } else {
      return new MockResponse(HttpStatus.SC_NOT_FOUND, method.name());
    }
  }

  private MockResponse handleResetPassword(HttpMethod method, String payload) {
    if (method.equals(HttpMethod.POST)) {
      return new MockResponse(HttpStatus.SC_CREATED, "{\"isNewPassword\":true}");
    } else {
      return new MockResponse(HttpStatus.SC_NOT_FOUND, method.name());
    }
  }

  private MockResponse handleNotify(HttpMethod method, String payload) {
    if (method.equals(HttpMethod.POST)) {
      return new MockResponse(HttpStatus.SC_CREATED, "");
    } else {
      return new MockResponse(HttpStatus.SC_NOT_FOUND, method.name());
    }
  }

  private MockResponse handleLoans(HttpMethod method, String id, String url,
                                   String payload, RoutingContext context) throws CQLParseException {
    String query = context.request().params().get("query");
    System.out.println("QUERY PARAMS: " + query);
    return handleBasicCrud(loansStorage, "loans", method, id, url, payload,
      context);
  }

  private MockResponse handleRequests(HttpMethod method, String id, String url,
                                   String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(requestsStorage, "requests", method, id, url, payload,
      context);
  }

  private MockResponse handleAccounts(HttpMethod method, String id, String url,
                                      String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(accountsStorage, "accounts", method, id, url, payload,
      context);
  }

  private MockResponse handleManualBlocks(HttpMethod method, String id, String url,
                                      String payload, RoutingContext context) throws CQLParseException {
    return handleBasicCrud(manualBlocksStorage, "manualblocks", method, id, url, payload,
      context);
  }

  private MockResponse handlerPasswordValidate(HttpMethod method, String id, String url,
                                                   String payload, RoutingContext context) {
    JsonObject object = new JsonObject(payload);
    String newPassword = object.getString("password");
    if (newPassword!=null && !newPassword.isEmpty()) {
      if (newPassword.equals("123456")) {
        return new MockResponse(200, new JsonObject()
          .put("result", "invalid")
          .put("messages", new JsonArray()
            .add("password.specialCharacter.invalid"))
          .encode());
      } else if (newPassword.equals("1q2w3E!190")) {
        return new MockResponse(200, new JsonObject()
          .put("result", "valid")
          .put("messages", new JsonArray())
          .encode());
      }
    }
    return new MockResponse(500, "");
  }

  private MockResponse handlerPasswordUpdate(HttpMethod method, String id, String url,
                                             String payload, RoutingContext context) {
    try {
      JsonObject object = new JsonObject(payload);
      UpdateCredentials updateCredentialsJson = object.mapTo(UpdateCredentials.class);
      if (updateCredentialsJson.getPassword().equals("123456")) {
        return new MockResponse(401, "Invalid password");
      } else if (updateCredentialsJson.getUserId().equals("99999999-9999-4999-9999-999999999991")) {
        return new MockResponse(400, "user not found");
      } else if (updateCredentialsJson.getPassword().equals("12345")
        && updateCredentialsJson.getNewPassword().equals("1q2w3E!190")
        && updateCredentialsJson.getUserId().equals("99999999-9999-4999-9999-999999999999")) {
        return new MockResponse(204, "");
      }
      return new MockResponse(500, "");
    } catch (Exception e) {
      return new MockResponse(500, "");
    }
  }

  private MockResponse handleBasicCrud(JsonStore store, String collectionName,
          HttpMethod method, String id, String url, String payload,
          RoutingContext context) throws CQLParseException {
    int code = 200;
    String response = null;
    if(method == GET) {
      if(id == null) { //Get collection
        MockCollection responseCollection = getCollectionWithContextParams(
                store, context);
        JsonObject responseObject = wrapCollection(responseCollection, collectionName);
        response = responseObject.encode();
      } else { //Get individual permission
        JsonObject item = store.getItem(id);
        if(item == null) {
          code = 404;
          response = "Item id '" + id + "' not found";
        } else {
          code = 200;
          response = item.encode();
        }
      }
    } else if(method == PUT) {
      boolean success = store.updateItem(id, new JsonObject(payload));
      if(success) {
        code = 204;
        response = "";
      } else {
        code = 404;
        response = "Not found";
      }
    } else if(method == POST) {
      JsonObject ob = null;
      try {
        ob = store.addItem(null, new JsonObject(payload));
      } catch(Exception e) {
        code = 422;
        response = "Unable to add item: " + e.getLocalizedMessage();
      }
      if(ob != null) {
        code = 201;
        response = ob.encode();
      }
    } else if(method == DELETE) {
      boolean success = store.deleteItem(id);
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

  private MockResponse handlePermsUsers(HttpMethod method, String id, String url,
          String payload, RoutingContext context) throws CQLParseException {
    System.out.println(String.format("Calling handlePermsUsers with id '%s' and url '%s'\n",
            id, url));
    int code = 200;
    String response = null;
    if(method == GET) {
      if(id == null) {
        System.out.println("Getting a list of permissions users\n");
        //Get list of perm users
        MockCollection userCollection = getCollectionWithContextParams(
                permsUsersStore, context);
        JsonObject responseObject = wrapCollection(userCollection, "permissionUsers");
        response = responseObject.encode();
      } else {
        if(!url.contains("/permissions")) {
          System.out.println("Getting a single perm user\n");
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
          System.out.println("Getting permissions for perm user " + id + "\n");
          JsonObject item = permsUsersStore.getItem(id);
          String full = context.request().getParam("full");
          String expanded = context.request().getParam("expanded");
          JsonArray permissions = item.getJsonArray("permissions");
          JsonObject permNameListObject = new JsonObject();
          code = 200;
          if( (expanded == null || !expanded.equals("true")) && (full == null ||
                  !full.equals("true")) ) {
            System.out.println("Getting unmodified permission list\n");
            permNameListObject.put("permissionNames", permissions);
            permNameListObject.put("totalRecords", permissions.size());
          } else if(expanded == null || !expanded.equals("true")) {
            //full only
            System.out.println("Getting full permission list\n");
            makeFullPerms(permissions, permsPermissionsStore);
            permNameListObject.put("permissionNames", permissions);
            permNameListObject.put("totalRecords", permissions.size());
          } else if(full == null || !full.equals("true")) {
            //expanded only
            System.out.println("Getting expanded permission list\n");
            JsonArray expandedPerms = recursePermList(permissions,
                    permsPermissionsStore);
            permNameListObject.put("permissionNames", expandedPerms);
            permNameListObject.put("totalRecords", expandedPerms.size());
          } else {
            //full and expanded
            System.out.println("Getting recursive AND expanded permission list\n");
            JsonArray expandedPerms = recursePermList(permissions,
                    permsPermissionsStore);
            makeFullPerms(expandedPerms, permsPermissionsStore);
            permNameListObject.put("permissionNames", expandedPerms);
            permNameListObject.put("totalRecords", expandedPerms.size());
          }
          response = permNameListObject.encode();
        }
      }
    } else if(method == POST) {
      //Create a new perm user
      JsonObject ob = null;
      try {
        ob = permsUsersStore.addItem(null, new JsonObject(payload));
      } catch(Exception e) {
        code = 422;
        response = "Unable to add object: " + e.getLocalizedMessage();
      }
      if(ob != null) {
        code = 201;
        response = ob.encode();
      }
    } else if(method == PUT) {
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
    } else if(method == DELETE) {
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
    if (response == null) {
      code = 500;
      response = "Server error";
    }
    return new MockResponse(code, response);
  }

  private JsonObject wrapCollection(MockCollection obCollection, String collectionName) {
    JsonObject result = new JsonObject();
    JsonArray obArray = new JsonArray();
    for(JsonObject ob : obCollection.getObjectList()) {
      obArray.add(ob);
    }
    result.put(collectionName, obArray);
    result.put("totalRecords", obCollection.getTotalRecords() );
    return result;
  }

  MockCollection getCollectionWithContextParams(JsonStore jsonStore,
          RoutingContext context) throws CQLParseException {
    String query = context.request().params().get("query");
    int offset = Integer.parseInt(getParamDefault(context, "offset", "0"));
    int limit = Integer.parseInt(getParamDefault(context, "limit", "30"));
    System.out.println(String.format("Params for request: query: %s, offset %s, limit %s",
            query, offset, limit));
    QuerySet qs = null;
    if (query != null) {
      if (query.contains(" ")) {
        Exception e = new IllegalArgumentException(
            "Space must be URL encoded as %20 but found: " + query);
        e.printStackTrace();
        // TODO when RMB's HttpClientInterface#chainedRequest correctly urlencodes CQL:
        // throw e;
      }
      query = StringUtil.urlDecode(query);
      qs = QuerySet.fromCQL(query);
    }
    return jsonStore.getCollection(offset, limit, qs);
  }

  private String getParamDefault(RoutingContext context, String param, String defaultValue) {
    String result = null;
    try {
      result = context.request().params().get(param);
    } catch(Exception e) {
      System.out.println(
              String.format("Unable to get param %s: %s", param,
              e.getLocalizedMessage()));
    }
    if(result == null) {
      return defaultValue;
    }
    return result;
  }

  private JsonObject getPermObject(String permName, JsonStore permStore) {
    Query query = new Query().setField("permissionName")
            .setOperator(Operator.EQUALS)
            .setValue(permName);
    QuerySet querySet = new QuerySet().setLeft(query)
            .setOperator(BooleanOperator.AND)
            .setRight(Boolean.TRUE);
    MockCollection obCollection = permStore.getCollection(0, 1, querySet);
    if(obCollection.getObjectList().isEmpty()) {
      return null;
    }
    return obCollection.getObjectList().get(0);
  }

  protected static Matcher parseMockUri(String uri, String endpoint) {
    Pattern pattern = Pattern.compile(
            //"([a-f0-9]+-[a-f0-9]+-[a-f0-9]+-[a-f0-9]+-[a-f0-9]+(\\/.+)?)?");
            "\\/([a-f0-9]+-[a-f0-9]+-[a-f0-9]+-[a-f0-9]+-[a-f0-9]+)(.+)?");
    String remainder = uri.substring(endpoint.length());
    Matcher matcher = pattern.matcher(remainder);
    return matcher;
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

  public static String getToken(String userId, String username, String tenant) {
    final String payload = new JsonObject()
      .put("user_id", userId)
      .put("sub", username)
      .put("tenant", tenant)
      .toString();

    return "header." + Base64.getEncoder().encodeToString(payload.getBytes()) + ".verify";
  }

  public static String getTokenWithoutUserId(String username, String tenant) {
    final String payload = new JsonObject()
      .put("sub", username)
      .put("tenant", tenant)
      .toString();

    return "header." + Base64.getEncoder().encodeToString(payload.getBytes()) + ".verify";
  }

  public static String getTokenWithoutTenant(String userId, String username) {
    final String payload = new JsonObject()
      .put("user_id", userId)
      .put("sub", username)
      .toString();

    return "header." + Base64.getEncoder().encodeToString(payload.getBytes()) + ".verify";
  }
}
