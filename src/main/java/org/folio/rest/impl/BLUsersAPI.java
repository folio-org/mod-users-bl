package org.folio.rest.impl;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.core.*;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.client.NotificationClient;
import org.folio.rest.client.UserModuleClient;
import org.folio.rest.client.impl.*;
import org.folio.rest.exception.UnprocessableEntityException;
import org.folio.rest.exception.UnprocessableEntityMessage;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.BlUsers;
import org.folio.rest.tools.client.BuildCQL;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.exceptions.PopulateTemplateException;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.util.ExceptionHelper;
import org.folio.rest.util.HttpClientUtil;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.service.PasswordResetLinkService;
import org.folio.service.PasswordResetLinkServiceImpl;
import org.folio.service.consortia.CrossTenantUserService;
import org.folio.service.consortia.CrossTenantUserServiceImpl;
import org.folio.service.password.UserPasswordService;
import org.folio.service.password.UserPasswordServiceImpl;
import org.folio.service.transactions.OpenTransactionsService;
import org.folio.service.transactions.OpenTransactionsServiceImpl;
import org.folio.util.PercentCodec;
import org.folio.util.StringUtil;

import javax.ws.rs.core.HttpHeaders;

import javax.ws.rs.core.MediaType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author shale
 *
 */
@SuppressWarnings("java:S1192")
// String literals should not be duplicated ("/perms/users", and "userId")
public class BLUsersAPI implements BlUsers {

  private static final String GROUPS_INCLUDE = "groups";
  private static final String PERMISSIONS_INCLUDE = "perms";
  private static final String PROXIESFOR_INCLUDE = "proxiesfor";
  private static final String SERVICEPOINTS_INCLUDE = "servicepoints";

  private static final String EXPANDED_PERMISSIONS_INCLUDE = "expanded_perms";
  private static final String EXPANDED_SERVICEPOINTS_INCLUDE = "expanded_servicepoints";
  private static final Logger logger = LogManager.getLogger(BLUsersAPI.class);

  public static final String OKAPI_URL_HEADER = "x-okapi-url";
  public static final String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  public static final String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

  public static final String LOCATE_USER_USERNAME = "userName";
  public static final String LOCATE_USER_PHONE_NUMBER = "phoneNumber";
  public static final String LOCATE_USER_MOBILE_PHONE_NUMBER = "mobilePhoneNumber";
  public static final String LOCATE_USER_EMAIL = "email";
  private static final List<String> DEFAULT_FIELDS_TO_LOCATE_USER =
    Arrays.asList("personal.email", "personal.phone", "personal.mobilePhone", "username");//NOSONAR

  private static final String USERNAME_LOCATED_EVENT_CONFIG_NAME = "USERNAME_LOCATED_EVENT";
  private static final String DEFAULT_NOTIFICATION_LANG = "en";

  private static final String FORGOTTEN_USERNAME_ERROR_KEY = "forgotten.username.found.multiple.users";
  private static final String FORGOTTEN_PASSWORD_ERROR_KEY = "forgotten.password.found.multiple.users";//NOSONAR
  public static final String FORGOTTEN_PASSWORD_FOUND_INACTIVE = "forgotten.password.found.inactive";//NOSONAR

  private static final String QUERY_LIMIT = "&limit=1000";

  private static final Pattern HOST_PORT_PATTERN = Pattern.compile("https?://([^:/]+)(?::?(\\d+)?)");
  private static final String UNDEFINED_USER = "UNDEFINED_USER__";

  private static final String LOGIN_ENDPOINT = "/authn/login-with-expiry";
  private static final String LOGIN_ENDPOINT_LEGACY = "/authn/login";
  private static final String FOLIO_ACCESS_TOKEN = "folioAccessToken";
  private static final String SET_COOKIE_HEADER = "Set-Cookie";

  private UserPasswordService userPasswordService;
  private PasswordResetLinkService passwordResetLinkService;
  private NotificationClient notificationClient;
  private OpenTransactionsService openTransactionsService;
  private UserModuleClient userClient;

  private CrossTenantUserService crossTenantUserService;

  public BLUsersAPI(Vertx vertx, String tenantId) { //NOSONAR
    this.userPasswordService = UserPasswordService
      .createProxy(vertx, UserPasswordServiceImpl.USER_PASS_SERVICE_ADDRESS);
    HttpClient httpClient = HttpClientUtil.getInstance(vertx);
    this.notificationClient = new NotificationClientImpl(httpClient);

    userClient = new UserModuleClientImpl(httpClient);
    passwordResetLinkService = new PasswordResetLinkServiceImpl(
      new ConfigurationClientImpl(httpClient),
      new AuthTokenClientImpl(httpClient),
      this.notificationClient,
      new PasswordResetActionClientImpl(httpClient),
      userClient,
      new UserPasswordServiceImpl(httpClient));

    openTransactionsService = new OpenTransactionsServiceImpl(
      new CirculationStorageModuleClientImpl(httpClient),
      new FeesFinesModuleClientImpl(httpClient),
      userClient
    );
    crossTenantUserService = new CrossTenantUserServiceImpl(httpClient);
  }

  private List<String> getDefaultIncludes(){
    List<String> defaultIncludes = new ArrayList<>();
    defaultIncludes.add(GROUPS_INCLUDE);
    defaultIncludes.add(PERMISSIONS_INCLUDE);
    defaultIncludes.add(SERVICEPOINTS_INCLUDE);
    return defaultIncludes;
  }

  @Override
  public void getBlUsersByUsernameByUsername(String username, List<String> include,
    boolean expandPerms, Map<String, String> okapiHeaders,
    Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
    Context vertxContext) {
    run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler);
  }

  Consumer<Response> handlePreviousResponse(boolean requireOneResult,
      boolean requireOneOrMoreResults, boolean stopChainOnNoResults,
      boolean[] previousFailure, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {
    return (response) -> {
      if(!previousFailure[0]){
        handleResponse(response, requireOneResult, requireOneOrMoreResults,
            stopChainOnNoResults, previousFailure, asyncResultHandler);
      }
    };
  }

  private void handleResponse(Response response, boolean requireOneResult,
      boolean requireOneOrMoreResults, boolean stopOnError, boolean previousFailure[],
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {

    if(previousFailure[0]){
      return;
    }
    if(isNull(response)){
      //response is null, meaning the previous call failed.
      //set previousFailure flag to true so that we don't send another error response to the client
      asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(
            "response is null from one of the services requested")));
      previousFailure[0] = true;
    } else {
      //check if the previous request failed
      int statusCode = response.getCode();
      boolean ok = Response.isSuccess(statusCode);
      if(ok){
        //the status code indicates success, check if the amount of results are acceptable from the
        //previous Response
        Integer totalRecords = response.getBody().getInteger("totalRecords");
        if(totalRecords == null){
          totalRecords = response.getBody().getInteger("total_records");
        }
        if(((totalRecords != null && totalRecords < 1) || response.getBody().isEmpty())
            && (requireOneResult || requireOneOrMoreResults)) {
          previousFailure[0] = true;
          if(stopOnError){
            //the chained requests will not fire the next request if the response's error object of the previous request is not null
            //so set the response's error object of the previous request to not null so that the calls that are to fire after this
            //are not called
            response.setError(new JsonObject());
          }
          logger.error("No record found for query '" + response.getEndpoint() + "'");
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond404WithTextPlain("No record found for query '"
                + response.getEndpoint() + "'")));
        } else if(totalRecords != null && totalRecords > 1 && requireOneResult) {
          logger.error("'" + response.getEndpoint() + "' returns multiple results");
          previousFailure[0] = true;
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond400WithTextPlain(("'" + response.getEndpoint()
                + "' returns multiple results"))));
        }
      } else {
        String message = "";
        String errorMessage;
        Errors errors = null;
        if(response.getError() != null){
          statusCode = response.getError().getInteger("statusCode");
          message = response.getError().encodePrettily();
          try {
            errorMessage = response.getError().getString("errorMessage");
            if (StringUtils.isNotEmpty(errorMessage)) {
              errors = (new JsonObject(errorMessage)).mapTo(Errors.class);
            }
          } catch (Exception e) {
            logger.debug(e.getMessage(), e);
          }
        } else{
          Throwable e = response.getException();
          if(e != null){
            message = response.getException().getLocalizedMessage();
            if(e instanceof PopulateTemplateException){
              return;
            }
          }
        }

        logger.error(message);
        previousFailure[0] = true;
        if(statusCode == 404){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond404WithTextPlain(message)));
        } else if(statusCode == 400){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond400WithTextPlain(message)));
        } else if(statusCode == 422){
          if (errors != null) {
            asyncResultHandler.handle(Future.succeededFuture(
              GetBlUsersByIdByIdResponse.respond422WithApplicationJson(errors)));
          } else {
            asyncResultHandler.handle(Future.succeededFuture(
              GetBlUsersByIdByIdResponse.respond400WithTextPlain(message)));
          }
        } else if(statusCode == 403){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond403WithTextPlain(message)));
        } else{
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond500WithTextPlain(message)));
        }
      }
    }
  }

  @Override
  public void getBlUsersByIdById(String userid, List<String> include, boolean expandPerms, Map<String, String> okapiHeaders,
    Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext) {
    run(userid, null, expandPerms, include, okapiHeaders, asyncResultHandler);
  }

  private void run(String userid, String username, Boolean expandPerms,
          List<String> include, Map<String, String> okapiHeaders,
          Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {

    //works on single user, no joins needed , just aggregate

    if(include == null || include.isEmpty()){
      //by default return perms and groups
      include = getDefaultIncludes();
    }

    boolean[] aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    okapiHeaders.remove(OKAPI_URL_HEADER);

    //HttpModuleClient2 client = new HttpModuleClient2(okapiURL, tenant);
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

    CompletableFuture<Response>[] userIdResponse = new CompletableFuture[1];
    String userTemplate = "";
    String groupTemplate = "";
    StringBuffer userUrl = new StringBuffer("/users");
    String mode[] = new String[1];
    try {
      if (userid != null) {
        userUrl.append("/").append(userid);
        userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
        userTemplate = "{id}";
        groupTemplate = "{patronGroup}";
        mode[0] = "id";
      } else if (username != null) {
        userUrl.append("?query=username==").append(username);
        userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
        userTemplate = "{users[0].id}";
        groupTemplate = "{users[0].patronGroup}";
        mode[0] = "username";
      }
    } catch (Exception ex) {
      client.closeClient();
      asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();
    logger.info(String.format("Received includes: %s", String.join(",", include)));

    for (int i = 0; i < includeCount; i++) {

      if (include.get(i).equals(PERMISSIONS_INCLUDE)){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users?query=userId=="+userTemplate,
              okapiHeaders, null, handlePreviousResponse(true, false, true,
              aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
      }
      else if(include.get(i).equals(GROUPS_INCLUDE)){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups/"+groupTemplate, okapiHeaders, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put(GROUPS_INCLUDE, groupResponse);
      }
      else if(include.get(i).equals(PROXIESFOR_INCLUDE)) {
        CompletableFuture<Response> proxiesforResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/proxiesfor?query=userId==" + userTemplate, okapiHeaders,
            null, handlePreviousResponse(true, false, true, aRequestHasFailed,
              asyncResultHandler)));
        requestedIncludes.add(proxiesforResponse);
        completedLookup.put(PROXIESFOR_INCLUDE, proxiesforResponse);
      }
      else if(include.get(i).equals(SERVICEPOINTS_INCLUDE)) {
        CompletableFuture<Response> servicePointsResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/service-points-users?query=userId==" + userTemplate + QUERY_LIMIT,
              okapiHeaders, null, handlePreviousResponse(false, false, false,
              aRequestHasFailed, asyncResultHandler))
        );
        requestedIncludes.add(servicePointsResponse);
        completedLookup.put(SERVICEPOINTS_INCLUDE, servicePointsResponse);
      }
    }
    if(expandPerms != null && expandPerms && completedLookup.containsKey(
        PERMISSIONS_INCLUDE)) {
      logger.info("Getting expanded permissions");
      CompletableFuture<Response> expandPermsResponse = completedLookup.get(
          PERMISSIONS_INCLUDE)
          .thenCompose(
              client.chainedRequest("/perms/users/{permissionUsers[0].id}/permissions?expanded=true&full=true",
              okapiHeaders, true, null, handlePreviousResponse(true, false, true,
              aRequestHasFailed, asyncResultHandler)));
      requestedIncludes.add(expandPermsResponse);
      completedLookup.put(EXPANDED_PERMISSIONS_INCLUDE, expandPermsResponse);
    }
    try {

      if (completedLookup.containsKey(SERVICEPOINTS_INCLUDE)) {
        CompletableFuture<Response> expandSPUResponse = expandServicePoints(
          completedLookup.get(SERVICEPOINTS_INCLUDE), client, aRequestHasFailed,
          okapiHeaders, asyncResultHandler);
        completedLookup.put(EXPANDED_SERVICEPOINTS_INCLUDE, expandSPUResponse);
        requestedIncludes.add(expandSPUResponse);

      }
    } catch (Exception ex) {
      client.closeClient();
      asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(
        new CompletableFuture[requestedIncludes.size()]))
    .thenAccept((response) -> {
      try {
        Response userResponse = userIdResponse[0].get();
        if(requestedIncludes.size() == 1){
          //no includes requested, so users response was not validated, so validate
          boolean requireOneResult;
          boolean requireOneOrMoreResults;
          if(mode[0].equals("id")){
            requireOneResult= true;
            requireOneOrMoreResults = false;
          }else{
            requireOneResult= true;
            requireOneOrMoreResults = true;
          }
          handleResponse(userResponse, requireOneResult, requireOneOrMoreResults, true, aRequestHasFailed, asyncResultHandler);
        }
        if(aRequestHasFailed[0]){
          return;
        }
        CompositeUser cu = new CompositeUser();
        if(mode[0].equals("id")){
          cu.setUser((User)userResponse.convertToPojo(User.class));
        }
        else if(mode[0].equals("username")){
          if(responseOk(userResponse)){
            cu.setUser((User)Response.convertToPojo(userResponse.getBody().getJsonArray("users").getJsonObject(0), User.class));
          }
        }
        CompletableFuture<Response> cf = completedLookup.get(GROUPS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          cu.setPatronGroup((PatronGroup)cf.get().convertToPojo(PatronGroup.class) );
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null && !cf.get().getBody().getJsonArray("permissionUsers").isEmpty()) {
          JsonObject permissionsJson = new JsonObject();
          permissionsJson.put("permissions", cf.get().getBody().getJsonArray("permissionUsers").getJsonObject(0).getJsonArray("permissions"));
          cu.setPermissions((Permissions)Response.convertToPojo(permissionsJson, Permissions.class));
        } else {
          cu.setPermissions(new Permissions());
        }

        cf = completedLookup.get(EXPANDED_PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          //data coming in from the service isnt returned as required by the composite user schema
          JsonObject j = new JsonObject();
          j.put("permissions", cf.get().getBody().getJsonArray("permissionNames"));
          cu.setPermissions((Permissions)Response.convertToPojo(j, Permissions.class));
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null && cf.get().getBody() != null){
          Permissions p = cu.getPermissions();
          if(p != null){
            //expanded permissions requested and the array of permissions has been populated
            //add the username
            //p.setId(cf.get().getBody().getString("username"));
          } else{
            cu.setPermissions((Permissions)Response.convertToPojo(cf.get().getBody(), Permissions.class));
          }
        }
        cf = completedLookup.get(PROXIESFOR_INCLUDE);
        if(cf != null && cf.get().getBody() != null) {
          JsonArray array = cf.get().getBody().getJsonArray("proxiesFor");
          List<ProxiesFor> proxyForList = new ArrayList<>();
          for(Object ob : array) {
            ProxiesFor proxyfor = new ProxiesFor();
            proxyfor = (ProxiesFor)Response.convertToPojo((JsonObject)ob, ProxiesFor.class);
            proxyForList.add(proxyfor);
          }
          if(!proxyForList.isEmpty()) {
            cu.setProxiesFor(proxyForList);
          }
        }

        fillCompositeUserWithServicePoint (completedLookup, cu);

        if(mode[0].equals("id")){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdByIdResponse.respond200WithApplicationJson(cu)));
        }else if(mode[0].equals("username")){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByUsernameByUsernameResponse.respond200WithApplicationJson(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          logger.error(e.getMessage(), e);
          if(mode[0].equals("id")){
            asyncResultHandler.handle(Future.succeededFuture(
              GetBlUsersByIdByIdResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }else if(mode[0].equals("username")){
            asyncResultHandler.handle(Future.succeededFuture(
              GetBlUsersByUsernameByUsernameResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }
        }
      } finally {
        client.closeClient();
      }
    });
  }

  @Override
  public void getBlUsers(String query, int offset, int limit,
      List<String> include, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler, Context vertxContext) {

    //works on multiple users, joins needed to aggregate

    boolean[] aRequestHasFailed = new boolean[]{false};
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);
    CompletableFuture<Response> []userIdResponse = new CompletableFuture[1];
    try {
      okapiHeaders.remove(OKAPI_URL_HEADER);

      if (include == null || include.isEmpty()) {
        //by default return perms and groups
        include = getDefaultIncludes();
      }

      StringBuffer userUrl = new StringBuffer("/users?");
      if (query != null) {
        userUrl.append("query=").append(PercentCodec.encode(query)).append("&");
      }
      userUrl.append("offset=").append(offset).append("&limit=").append(limit);
      userIdResponse[0] = client.request(userUrl.toString(), okapiHeaders);
    } catch (Exception ex) {
      client.closeClient();
      asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByIdByIdResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      return;
    }
    int includeCount = include.size();
    ArrayList<CompletableFuture<Response>> requestedIncludes = new ArrayList<>();
    //CompletableFuture<Response> []requestedIncludes = new CompletableFuture[includeCount+1];
    Map<String, CompletableFuture<Response>> completedLookup = new HashMap<>();

    for (int i = 0; i < includeCount; i++) {

      if (include.get(i).equals(PERMISSIONS_INCLUDE)){
        //call perms once the /users?query=username={username} (same as creds) completes
        CompletableFuture<Response> permResponse = userIdResponse[0].thenCompose(
              client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
                handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(permResponse);
        completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
      }
      else if(include.get(i).equals(GROUPS_INCLUDE)){
        CompletableFuture<Response> groupResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/groups", okapiHeaders, new BuildCQL(null, "users[*].patronGroup", "id"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(groupResponse);
        completedLookup.put(GROUPS_INCLUDE, groupResponse);
      }
      else if(include.get(i).equals(PROXIESFOR_INCLUDE)) {
        CompletableFuture<Response> proxiesforResponse = userIdResponse[0].thenCompose(
          client.chainedRequest("/proxiesfor", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(proxiesforResponse);
        completedLookup.put(PROXIESFOR_INCLUDE, proxiesforResponse);
      }
    }
    requestedIncludes.add(userIdResponse[0]);
    CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
    .thenAccept((response) -> {
      try {
        Response userResponse = userIdResponse[0].get();
        if(requestedIncludes.size() == 1){
          //no includes requested, so users response was not validated, so validate
          handleResponse(userResponse, false, true, true, aRequestHasFailed, asyncResultHandler);
        }
        if(aRequestHasFailed[0]){
          return;
        }
        CompositeUserListObject cu = new CompositeUserListObject();
        Response composite = new Response();
        //map an array of users returned by /users into an array of compositeUser objects - "compositeUser": []
        //name each object in the array "users" -  "compositeUser": [ { "users": { ...
        composite.mapFrom(userResponse, "users[*]", "compositeUser", "users", true);
        if(composite.getBody().isEmpty()){
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(Future.succeededFuture(
              GetBlUsersResponse.respond200WithApplicationJson(cu)));
          }
          aRequestHasFailed[0] = true;
          return;
        }
        Response groupResponse = null;
        Response permsResponse = null;
        Response proxiesforResponse = null;
        CompletableFuture<Response> cf = completedLookup.get(GROUPS_INCLUDE);
        if(cf != null){
          groupResponse = cf.get();
          //check for errors
          handleResponse(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            //join into the compositeUser array groups joining on id and patronGroup field values. assume only one group per user
            //hence the usergroup[0] field to push into ../../groups otherwise (if many) leave out the [0] and pass in "usergroups"
            composite.joinOn("compositeUser[*].users.patronGroup", groupResponse, "usergroups[*].id", "../", "../../groups", false);
          }
        }
        cf = completedLookup.get(PERMISSIONS_INCLUDE);
        if(cf != null){
          permsResponse = cf.get();
          //check for errors
          handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]){
            composite.joinOn("compositeUser[*].users.id", permsResponse, "permissionUsers[*].userId", "../permissions", "../../permissions.permissions", false);
          }
        }
        cf = completedLookup.get(PROXIESFOR_INCLUDE);
        if(cf != null) {
          proxiesforResponse = cf.get();
          handleResponse(proxiesforResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
          if(!aRequestHasFailed[0]) {
            composite.joinOn("compositeUser[*].users.id", proxiesforResponse, "proxiesFor[*].userId", "../", "../../proxiesFor", false);
          }
        }
        @SuppressWarnings("unchecked")
        List<CompositeUser> cuol = (List<CompositeUser>)Response.convertToPojo(composite.getBody().getJsonArray("compositeUser"), CompositeUser.class);
        cu.setCompositeUsers(cuol);
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersResponse.respond200WithApplicationJson(cu)));
        }
      } catch (Exception e) {
        if(!aRequestHasFailed[0]){
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersResponse.respond500WithTextPlain(e.getLocalizedMessage())));
        }
        logger.error(e.getMessage(), e);
      } finally {
        client.closeClient();
      }
    });
  }

  @Override
  public void getBlUsersByUsernameOpenTransactionsByUsername(String username, Map<String, String> okapiHeaders,
                                                             Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                                             Context vertxContext) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    userClient.lookupUserByUserName(username, connectionParams)
      .onSuccess(user -> {
        if (user.isPresent()) {
          getTransactionsOfUser(user.get(), connectionParams, asyncResultHandler);
        } else {
          String msg = String.format("Users with username '%s' not found", username);
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByUsernameOpenTransactionsByUsernameResponse.respond404WithTextPlain(msg)));
        }
      })
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByUsernameOpenTransactionsByUsernameResponse.respond500WithTextPlain(error.getLocalizedMessage()))));
  }

  @Override
  public void getBlUsersByIdOpenTransactionsById(String id, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                                 Context vertxContext) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    userClient.lookupUserById(id, connectionParams)
      .onSuccess(user -> {
        if (user.isPresent()) {
          getTransactionsOfUser(user.get(), connectionParams, asyncResultHandler);
        } else {
          String msg = String.format("User with id '%s' not found", id);
          asyncResultHandler.handle(Future.succeededFuture(
            GetBlUsersByIdOpenTransactionsByIdResponse.respond404WithTextPlain(msg)));
        }
      })
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        GetBlUsersByIdOpenTransactionsByIdResponse.respond500WithTextPlain(error.getLocalizedMessage()))));
  }

  private void getTransactionsOfUser(User user, OkapiConnectionParams connectionParams,
                               Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler) {
    openTransactionsService.getTransactionsOfUser(user, connectionParams)
      .onSuccess(userTransactions ->
        asyncResultHandler.handle(Future.succeededFuture(
          GetBlUsersByIdOpenTransactionsByIdResponse.respond200WithApplicationJson(userTransactions))))
      .onFailure(error ->
        asyncResultHandler.handle(Future.succeededFuture(
          GetBlUsersByIdOpenTransactionsByIdResponse.respond500WithTextPlain(error.getLocalizedMessage()))));
  }

  @Override
  public void deleteBlUsersByIdById(String id, List<String> include,
                                    Map<String, String> okapiHeaders,
                                    Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                    Context vertxContext) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    userClient.lookupUserById(id, connectionParams)
      .onSuccess(user -> {
        if (user.isPresent()) {
          openTransactionsService.getTransactionsOfUser(user.get(), connectionParams)
            .onSuccess(userTransactions -> {
              if (Boolean.TRUE.equals(userTransactions.getHasOpenTransactions())) {
                asyncResultHandler.handle(Future.succeededFuture(
                  DeleteBlUsersByIdByIdResponse.respond409WithApplicationJson(userTransactions)
                ));
              } else {
                userClient.deleteUserById(user.get().getId(), connectionParams)
                  .onSuccess(boolResult ->
                    asyncResultHandler.handle(Future.succeededFuture(
                      DeleteBlUsersByIdByIdResponse.respond204()
                    )))
                .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
                  DeleteBlUsersByIdByIdResponse.respond500WithTextPlain(error.getLocalizedMessage())
                  )));
              }
            })
          .onFailure(error ->
            asyncResultHandler.handle(Future.succeededFuture(
              DeleteBlUsersByIdByIdResponse.respond500WithTextPlain(error.getLocalizedMessage())
            )));
        } else {
          String msg = String.format("User with id '%s' not found", id);
          asyncResultHandler.handle(Future.succeededFuture(
            DeleteBlUsersByIdByIdResponse.respond404WithTextPlain(msg)));
        }
      })
      .onFailure(error -> asyncResultHandler.handle(Future.succeededFuture(
        DeleteBlUsersByIdByIdResponse.respond500WithTextPlain(error.getLocalizedMessage()))));
  }

  private boolean responseOk(Response r){
    if(r != null && r.getBody() != null){
      return true;
    }
    return false;
  }

  private boolean isNull(Response r){
    if(r != null){
      return false;
    }
    return true;
  }

  private String getUsername(String token) {
    JsonObject payload = parseTokenPayload(token);
    if(payload == null) { return null; }
    String username = payload.getString("sub");
    return username;
  }

  private String getUserId(String token) {
    JsonObject payload = parseTokenPayload(token);
    if (payload == null) {
      return null;
    }
    return payload.getString("user_id");
  }

  private String getTenant(String token) {
    JsonObject payload = parseTokenPayload(token);
    if (payload == null) {
      return null;
    }
    return payload.getString("tenant");
  }

  private JsonObject parseTokenPayload(String token) {
    String[] tokenParts = token.split("\\.");
    if (tokenParts.length == 3) {
      String encodedPayload = tokenParts[1];
      byte[] decodedJsonBytes = Base64.getDecoder().decode(encodedPayload);
      String decodedJson = new String(decodedJsonBytes);
      return new JsonObject(decodedJson);
    } else {
      return null;
    }
  }

  @Override
  public void getBlUsersSelf(List<String> include, boolean expandPerms,
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
          Context vertxContext) {
    String token = okapiHeaders.get(OKAPI_TOKEN_HEADER);
    String username = getUsername(token);
    String userId = getUserId(token);
    if (StringUtils.isBlank(username) || username.startsWith(UNDEFINED_USER) || StringUtils.isBlank(userId)) {
      run(null, username, expandPerms, include, okapiHeaders, asyncResultHandler);
    } else {
      run(userId, null, expandPerms, include, okapiHeaders, asyncResultHandler);
    }
  }

  @Override
  public void postBlUsersLoginWithExpiry(boolean expandPerms, List<String> include, String userAgent, String xForwardedFor,
      LoginCredentials entity, Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) {
    doPostBlUsersLogin(expandPerms, include, userAgent, xForwardedFor, entity, okapiHeaders, asyncResultHandler,
        LOGIN_ENDPOINT, this::loginResponse);
  }

  @Override
  public void postBlUsersLogin(boolean expandPerms, List<String> include, String userAgent, String xForwardedFor,
      LoginCredentials entity, Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      Context vertxContext) {
    doPostBlUsersLogin(expandPerms, include, userAgent, xForwardedFor, entity, okapiHeaders, asyncResultHandler,
        LOGIN_ENDPOINT_LEGACY, this::loginResponseLegacy);
  }

  @SuppressWarnings("java:S1874")
  private void doPostBlUsersLogin(boolean expandPerms, List<String> include, String userAgent, String xForwardedFor, //NOSONAR
      LoginCredentials entity, Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
      String loginEndpoint, BiFunction<Response, CompositeUser, javax.ws.rs.core.Response> respond) {

    //works on single user, no joins needed , just aggregate
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    okapiHeaders.remove(OKAPI_URL_HEADER);

    boolean []aRequestHasFailed = new boolean[]{false};

    if(include == null || include.isEmpty()){
      //by default return perms and groups
      include = getDefaultIncludes();
    }

    if (entity == null || entity.getUsername() == null || entity.getPassword() == null) {
      asyncResultHandler.handle(Future.succeededFuture(
        PostBlUsersLoginResponse.respond400WithTextPlain("Improperly formatted request")));
    } else {
      HttpClientInterface clientForLogin = HttpClientFactory.getHttpClient(okapiURL, okapiHeaders.get(OKAPI_TENANT_HEADER));
      String moduleURL = "/authn/login";
      logger.debug("Requesting login from " + moduleURL);
      //can only be one user with this username - so only one result expected
      var cql = "username==" + StringUtil.cqlEncode(entity.getUsername());
      var userUrl = "/users?query=" + PercentCodec.encode(cql);
      //run login
      try {
        Map<String, String> headers = new CaseInsensitiveMap<>(okapiHeaders);
        Optional.ofNullable(userAgent)
          .ifPresent(header -> headers.put(HttpHeaders.USER_AGENT, header));
        Optional.ofNullable(xForwardedFor)
          .ifPresent(header -> headers.put(X_FORWARDED_FOR_HEADER, header));

        List<String> finalInclude = include;

        clientForLogin.request(HttpMethod.POST, entity, loginEndpoint, headers)
          .thenAccept(loginResponse -> {
            //then get user by username, inject okapi headers from the login response into the user request
            //see 'true' flag passed into the chainedRequest
            handleResponse(loginResponse, false, false, true, aRequestHasFailed, asyncResultHandler);

            String token = getToken(loginResponse.getHeaders());
            String tenant = getTenant(token);
            okapiHeaders.put(OKAPI_TENANT_HEADER, tenant);
            HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);

            try {
              getUserWithPerms(expandPerms, okapiHeaders, asyncResultHandler, userUrl, finalInclude, tenant, loginResponse, client, respond);
            } catch (Exception e) {
              client.closeClient();
              asyncResultHandler.handle(Future.succeededFuture(
                PostBlUsersLoginResponse.respond500WithTextPlain(e.getLocalizedMessage())));
            } finally {
              clientForLogin.closeClient();
            }
          })
          .exceptionally(throwable -> {
            clientForLogin.closeClient();
            asyncResultHandler.handle(Future.succeededFuture(
              PostBlUsersLoginResponse.respond500WithTextPlain(throwable.getLocalizedMessage())));
            return null;
          });
      } catch (Exception ex) {
        clientForLogin.closeClient();
        asyncResultHandler.handle(Future.succeededFuture(
          PostBlUsersLoginResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
      }
    }
  }

  private String getToken(MultiMap headers) {
    // There is a legacy token mode and a non-legacy mode. The non-legacy mode gets the token from a Set-Cookie header.
    // The legacy mode gets it from the X-Okapi-Token header.
    for (var header : headers.getAll(SET_COOKIE_HEADER)) {
      Cookie cookie = ClientCookieDecoder.STRICT.decode(header.trim());
      if (cookie.name().equals(FOLIO_ACCESS_TOKEN)) {
        return cookie.value();
      }
    }

    return headers.get(OKAPI_TOKEN_HEADER);
  }

  @SuppressWarnings({"java:S107", "java:S3776", "java:S1874"})
  private void getUserWithPerms(boolean expandPerms,
                                Map<String, String> okapiHeaders,
                                Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                String userUrl,
                                List<String> include,
                                String tenant,
                                Response loginResponse,
                                HttpClientInterface client,
                                BiFunction<Response, CompositeUser, javax.ws.rs.core.Response> respond) throws Exception {

      CompletableFuture<Response> userResponse[] = new CompletableFuture[1];
      boolean []aRequestHasFailed = new boolean[]{false};
      ArrayList<CompletableFuture<Response>> requestedIncludes
          = new ArrayList<>();
      Map<String, CompletableFuture<Response>> completedLookup
          = new HashMap<>();

      userResponse[0] = client.request(HttpMethod.GET, userUrl, okapiHeaders);

      for (int i = 0; i < include.size(); i++) {

        if (include.get(i).equals(PERMISSIONS_INCLUDE)){
          //call perms once the /users?query=username={username} (same as creds) completes
          CompletableFuture<Response> permResponse = userResponse[0].thenCompose(
                client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
                  handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(permResponse);
          completedLookup.put(PERMISSIONS_INCLUDE, permResponse);
        }
        else if(include.get(i).equals(GROUPS_INCLUDE)){
          CompletableFuture<Response> groupResponse = userResponse[0].thenCompose(
            client.chainedRequest("/groups/{users[0].patronGroup}", okapiHeaders, null,
              handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler)));
          requestedIncludes.add(groupResponse);
          completedLookup.put(GROUPS_INCLUDE, groupResponse);
        }
        else if(include.get(i).equals(SERVICEPOINTS_INCLUDE)) {
          CompletableFuture<Response> servicePointsResponse = userResponse[0].thenCompose(
            client.chainedRequest("/service-points-users?query=userId=={users[0].id}" + QUERY_LIMIT,
                okapiHeaders, null, handlePreviousResponse(false, false, false,
                aRequestHasFailed, asyncResultHandler))
          );
          requestedIncludes.add(servicePointsResponse);
          completedLookup.put(SERVICEPOINTS_INCLUDE, servicePointsResponse);
          try { //NOSONAR
            CompletableFuture<Response> expandSPUResponse = expandServicePoints(
              servicePointsResponse, client, aRequestHasFailed, okapiHeaders,
              asyncResultHandler);
            completedLookup.put(EXPANDED_SERVICEPOINTS_INCLUDE, expandSPUResponse);
            requestedIncludes.add(expandSPUResponse);
          } catch (Exception ex) {
            client.closeClient();
            asyncResultHandler.handle(Future.succeededFuture(
              PostBlUsersLoginResponse.respond500WithTextPlain(ex.getLocalizedMessage())));
          }
        }
      }

      if (expandPerms){
        CompletableFuture<Response> permUserResponse = userResponse[0].thenCompose(
          client.chainedRequest("/perms/users", okapiHeaders, new BuildCQL(null, "users[*].id", "userId"),
            handlePreviousResponse(false, true, true, aRequestHasFailed, asyncResultHandler))
        );
        CompletableFuture<Response> expandPermsResponse = permUserResponse.thenCompose(
          client.chainedRequest("/perms/users/{permissionUsers[0].id}/permissions?expanded=true&full=true", okapiHeaders, true, null,
            handlePreviousResponse(true, false, true, aRequestHasFailed, asyncResultHandler)));
        requestedIncludes.add(expandPermsResponse);
        completedLookup.put(EXPANDED_PERMISSIONS_INCLUDE, expandPermsResponse);
      }
      requestedIncludes.add(userResponse[0]);

      CompletableFuture.allOf(requestedIncludes.toArray(new CompletableFuture[requestedIncludes.size()]))
      .thenAccept((response) -> {
        try {
          if(requestedIncludes.size() == 1){
            //no includes requested, so users response was not validated, so validate
            handleResponse(userResponse[0].get(), true, false, true, aRequestHasFailed, asyncResultHandler);
          }
          if(aRequestHasFailed[0]){
            return;
          }
          //all requested endpoints have completed, proces....
          CompositeUser cu = new CompositeUser().withTenant(tenant);
          //user errors handled in chainedRequest, so assume user is ok at this point
          cu.setUser((User)Response.convertToPojo(
            userResponse[0].get().getBody().getJsonArray("users").getJsonObject(0), User.class));

          CompletableFuture<Response> cf = completedLookup.get(GROUPS_INCLUDE);
          if(cf != null){
            Response groupResponse = cf.get();
            handleResponse(groupResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && groupResponse.getBody() != null){
              cu.setPatronGroup((PatronGroup)Response.convertToPojo(groupResponse.getBody(), PatronGroup.class));
            }
          }
          cf = completedLookup.get(EXPANDED_PERMISSIONS_INCLUDE);
          if(cf != null){
            Response permsResponse = cf.get();
            handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0] && permsResponse.getBody() != null){
              //data coming in from the service isnt returned as required by the composite user schema
              JsonObject j = new JsonObject();
              j.put("permissions", permsResponse.getBody().getJsonArray("permissionNames"));
              cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));
            }
          }
          cf = completedLookup.get(PERMISSIONS_INCLUDE);
          if(cf != null && cf.get().getBody() != null){
            Response permsResponse = cf.get();
            handleResponse(permsResponse, false, true, false, aRequestHasFailed, asyncResultHandler);
            if(!aRequestHasFailed[0]){
              Permissions p = cu.getPermissions();
              if(p != null){
                //expanded permissions requested and the array of permissions has been populated
                //add the username
                p.setUserId(permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0).getString("id"));
              } else{
                //data coming in from the service isnt returned as required by the composite user schema
                JsonObject j = permsResponse.getBody().getJsonArray("permissionUsers").getJsonObject(0);
                cu.setPermissions((Permissions) Response.convertToPojo(j, Permissions.class));
              }
            }
          }

          //TODO: Refactor so less copy/paste

          fillCompositeUserWithServicePoint(completedLookup, cu);

          if(!aRequestHasFailed[0]){
            var r = respond.apply(loginResponse, cu);
            asyncResultHandler.handle(Future.succeededFuture(r));
          }
        } catch (Exception e) {
          if(!aRequestHasFailed[0]){
            asyncResultHandler.handle(Future.succeededFuture(
              PostBlUsersLoginResponse.respond500WithTextPlain(e.getLocalizedMessage())));
          }
          logger.error(e.getMessage(), e);
        } finally {
          client.closeClient();
        }
      });
  }

  private static void fillCompositeUserWithServicePoint(Map<String, CompletableFuture<Response>> completedLookup, CompositeUser cu) throws Exception {
    CompletableFuture<Response> cf;
    cf = completedLookup.get(SERVICEPOINTS_INCLUDE);
    CompletableFuture<Response> ecf = completedLookup.get(
        EXPANDED_SERVICEPOINTS_INCLUDE);
    if(ecf != null && cf != null && cf.get().getBody() != null) {
      JsonArray array = cf.get().getBody().getJsonArray("servicePointsUsers");
      if(!array.isEmpty()) {
        JsonObject spuJson = array.getJsonObject(0);
        ServicePointsUser spu = (ServicePointsUser)Response.convertToPojo(spuJson,
            ServicePointsUser.class);
        List<ServicePoint> spList = new ArrayList<>();
        Response resp = ecf.get();
        if(resp != null) {
          JsonObject spCollectionJson = resp.getBody();
          if(spCollectionJson != null) {
            JsonArray spArray = spCollectionJson.getJsonArray("servicepoints");
            if(spArray != null) {
              for(Object ob: spArray) {
                JsonObject json = (JsonObject)ob;
                ServicePoint sp = (ServicePoint)Response.convertToPojo(json,
                    ServicePoint.class);
                spList.add(sp);
              }
              spu.setServicePoints(spList);
            }
          }
        }
        cu.setServicePointsUser(spu);
      }
    }
  }

  @SuppressWarnings("java:S1874")
  private javax.ws.rs.core.Response loginResponseLegacy(Response loginResponse, CompositeUser cu) {
    String token = String.valueOf(loginResponse.getHeaders().get(OKAPI_TOKEN_HEADER));
    return PostBlUsersLoginResponse.respond201WithApplicationJson(cu,
      PostBlUsersLoginResponse.headersFor201().withXOkapiToken(token));
  }

  @SuppressWarnings("java:S1874")
  private javax.ws.rs.core.Response loginResponse(Response loginResponse, CompositeUser cu) {
    JsonObject body = loginResponse.getBody();
    String refreshTokenExpiration = body.getString("refreshTokenExpiration");
    String accessTokenExpiration = body.getString("accessTokenExpiration");
    var tokenExpiration = new TokenExpiration();
    tokenExpiration.setAccessTokenExpiration(accessTokenExpiration);
    tokenExpiration.setRefreshTokenExpiration(refreshTokenExpiration);
    cu.setTokenExpiration(tokenExpiration);

    // Use the ResponseBuilder rather than RMB-generated code. We need to do this because
    // RMB generated-code does not allow multiple headers with the same key, which is what we need
    // here. This is a permanent workaround as long as mod-users-bl uses RMB.
    var responseBuilder = javax.ws.rs.core.Response.status(201)
        .type(MediaType.APPLICATION_JSON)
        .entity(cu);
    for (String cookie : loginResponse.getHeaders().getAll("Set-Cookie")) {
      responseBuilder.header("Set-Cookie", cookie);
    }
    return responseBuilder.build();
  }

  private CompletableFuture<Response> expandServicePoints(
      CompletableFuture<Response> spuResponseFuture, HttpClientInterface client,
      boolean[] aRequestHasFailed, Map<String, String> okapiHeaders,
      Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler)
      throws InterruptedException, ExecutionException {
    if(spuResponseFuture == null) {
      return CompletableFuture.completedFuture(null);
    }
    return spuResponseFuture.thenCompose( response -> {
      List<String> servicePointIdQueryList = new ArrayList<>();

      JsonObject servicePointsUserListObjectJson = response.getBody();
      if(servicePointsUserListObjectJson == null ) {
        return CompletableFuture.completedFuture(null);
      }
      JsonObject servicePointsUserJson = null;
      try {
        servicePointsUserJson = servicePointsUserListObjectJson
            .getJsonArray("servicePointsUsers").getJsonObject(0);
      } catch(Exception e) {
        //meh
      }
      if(servicePointsUserJson == null) {
        return CompletableFuture.completedFuture(null);
      }
      if(servicePointsUserJson.containsKey("defaultServicePointId")) {
        String defaultSPId = servicePointsUserJson.getString("defaultServicePointId");
        if(defaultSPId != null) {
          servicePointIdQueryList.add(String.format("id==\"%s\"", defaultSPId));
        }
      }
      if(servicePointsUserJson.containsKey("servicePointsIds")) {
        JsonArray SPIdArray = servicePointsUserJson.getJsonArray("servicePointsIds");
        if(SPIdArray != null) {
          for(Object ob : SPIdArray) {
            servicePointIdQueryList.add(String.format("id==\"%s\"", (String)ob));
          }
        }
      }
      if(servicePointIdQueryList.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      String idQuery = StringUtil.urlEncode(String.join(" or ", servicePointIdQueryList));
      CompletableFuture<Response> expandSPUResponse = spuResponseFuture
          .thenCompose(client.chainedRequest("/service-points?query="+ idQuery + QUERY_LIMIT,
          okapiHeaders, true, null, handlePreviousResponse(false, false, false,
          aRequestHasFailed, asyncResultHandler)));

      return expandSPUResponse;
    });
  }

  /**
   *
   * @param locateUserFields - a list of fields to be used for search
   * @param value            - a value to search
   * @return
   */
  private String buildQuery(List<String> locateUserFields, String value) {
    return locateUserFields.stream()
      .map(field -> new StringBuilder(field).append("==\"").append(value).append("\"").toString())
      .collect(Collectors.joining(" or "));
  }

  /**
   * Maps aliases to configuration parameters
   * @param fieldAliasList - a list of aliases
   * @return a list of user fields to use for search
   */
  private Future<List<String>> getLocateUserFields(List<String> fieldAliasList, Map<String, String> okapiHeaders) {

    try {
      String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
      String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
      String token = okapiHeaders.get(OKAPI_TOKEN_HEADER);

      Matcher matcher = HOST_PORT_PATTERN.matcher(okapiURL);
      if (!matcher.find()) {
        return Future.failedFuture("Could not parse okapiURL: " + okapiURL);
      }

      ConfigurationsClient configurationsClient = new ConfigurationsClient(okapiURL, tenant, token);
      StringBuilder query = new StringBuilder("module==USERSBL AND (")
          .append(fieldAliasList.stream()
              .map(f -> new StringBuilder("code==\"").append(f).append("\"").toString())
              .collect(Collectors.joining(" or ")))
          .append(")");

      return configurationsClient.getConfigurationsEntries(query.toString(), 0, 3, null, null)
          .map(result -> {
            if (result.statusCode() != 200) {
              throw new RuntimeException("Expected status code 200, got '" + result.statusCode() +
                  "' :" + result.bodyAsString());
            }

            JsonObject entries = result.bodyAsJsonObject();

            if (entries.getJsonArray("configs").isEmpty()) {
              return DEFAULT_FIELDS_TO_LOCATE_USER;
            }
            return entries.getJsonArray("configs").stream()
                .map(o -> ((JsonObject) o).getString("value"))
                .flatMap(s -> Stream.of(s.split("[^\\w\\.]+")))
                .collect(Collectors.toList());
          });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }


  /**
   *
   * @param fieldAliasList list of aliases to use [LOCATE_USER_USERNAME LOCATE_USER_PHONE_NUMBER LOCATE_USER_EMAIL]
   * @param entity - an identity with a value
   * @param okapiHeaders
   * @return
   */
  private Future<User> locateUserByAlias(List<String> fieldAliasList, Identifier entity,
                                         Map<String, String> okapiHeaders, String errorKey) {
    return getLocateUserFields(fieldAliasList, okapiHeaders)
      .compose(locateUserFieldsAR -> crossTenantUserService.findCrossTenantUser(entity.getId(), okapiHeaders, errorKey)
        .compose(user -> {
          if (user == null) {
            return locateUser(locateUserFieldsAR, entity, okapiHeaders, errorKey);
          }
          return Future.succeededFuture(user);
          }));
  }

  /**
   * Searching user by query
   * @param locateUserFields a list of user fields to use for search
   * @param entity - an identity with a value
   * @param okapiHeaders request headers
   * @return User
   */
  private Future<User> locateUser(List<String> locateUserFields, Identifier entity, Map<String, String> okapiHeaders, String errorKey) {
    String tenant = okapiHeaders.get(OKAPI_TENANT_HEADER);
    String okapiURL = okapiHeaders.get(OKAPI_URL_HEADER);
    Promise<User> asyncResult = Promise.promise();
    HttpClientInterface client = HttpClientFactory.getHttpClient(okapiURL, tenant);
    String query = buildQuery(locateUserFields, entity.getId());
    try {
      String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
      String userUrl = String.format("/users?query=%s&offset=0&limit=2", encodedQuery);

      client.request(userUrl, okapiHeaders).thenAccept(userResponse -> {
        String noUserFoundMessage = "User is not found: ";

        if (!responseOk(userResponse)) {
          asyncResult.fail(new NoSuchElementException(noUserFoundMessage + entity.getId()));
          return;
        }

        JsonArray users = userResponse.getBody().getJsonArray("users");
        int arraySize = users.size();
        if (arraySize == 0) {
          asyncResult.fail(new NoSuchElementException(noUserFoundMessage + entity.getId()));
          return;
        } else if (arraySize > 1) {
          String message = String.format("Multiple users associated with '%s'", entity.getId());
          UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(errorKey, message);
          asyncResult.fail(new UnprocessableEntityException(Collections.singletonList(entityMessage)));
          return;
        }
        try {
          User user = (User) Response.convertToPojo(users.getJsonObject(0), User.class);
          if (user != null && !user.getActive()) {
            String message = String.format("Users associated with '%s' is not active", entity.getId());
            UnprocessableEntityMessage entityMessage = new UnprocessableEntityMessage(FORGOTTEN_PASSWORD_FOUND_INACTIVE, message);
            throw new UnprocessableEntityException(Collections.singletonList(entityMessage));
          }
          asyncResult.complete(user);
        } catch (Exception e) {
          asyncResult.fail(e);
        }
      });
    } catch (Exception e) {
      asyncResult.fail(e);
    }
    return asyncResult.future()
      .onComplete(x -> client.closeClient());
  }

  /*
   * See MODLOGIN-44-45
   *
   * These methods (postBlUsersForgottenPassword & postBlUsersForgottenUsername) rely on the configuration properties from mod-configuration
   * see BLUsersAPITest.insertData for example
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "userName", "description" : "if true userName will be used fot forgot password search", "default" : false, "enabled" : true, "value" : "username" }
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "phoneNumber", "description" : "if true personal.phone & personal.mobilePhone will be used for forgot password and forgot user name search", "default" : false, "enabled" : true, "value" : "personal.phone, personal.mobilePhone" }
   * { "module" : "USERSBL", "configName" : "fogottenData", "code" : "email", "description" : "if true personal.email will be used for forgot password and forgot user name search", "default" : false, "enabled" : true, "value" : "personal.email" }
   */
  @Override
  public void postBlUsersForgottenPassword(Identifier entity, Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>>asyncResultHandler, Context vertxContext) {
    locateUserByAlias(Arrays.asList(LOCATE_USER_USERNAME, LOCATE_USER_PHONE_NUMBER, LOCATE_USER_EMAIL), entity, okapiHeaders, FORGOTTEN_PASSWORD_ERROR_KEY)
      .compose(user -> passwordResetLinkService.sendPasswordResetLink(user, okapiHeaders))
      .map(PostBlUsersForgottenPasswordResponse.respond204())
      .map(javax.ws.rs.core.Response.class::cast)
      .otherwise(ExceptionHelper::handleException)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postBlUsersForgottenUsername(Identifier entity, Map<String, String> okapiHeaders, Handler<AsyncResult<javax.ws.rs.core.Response>>asyncResultHandler, Context vertxContext) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders);
    locateUserByAlias(Arrays.asList(LOCATE_USER_PHONE_NUMBER, LOCATE_USER_EMAIL), entity, okapiHeaders, FORGOTTEN_USERNAME_ERROR_KEY)
      .compose(user -> {
        Notification notification = new Notification()
          .withEventConfigName(USERNAME_LOCATED_EVENT_CONFIG_NAME)
          .withRecipientId(user.getId())
          .withText(StringUtils.EMPTY)
          .withLang(DEFAULT_NOTIFICATION_LANG)
          .withContext(new org.folio.rest.jaxrs.model.Context()
            .withAdditionalProperty("user", user)
          );
        return notificationClient.sendNotification(notification, connectionParams);
      })
      .map(PostBlUsersForgottenPasswordResponse.respond204())
      .map(javax.ws.rs.core.Response.class::cast)
      .otherwise(ExceptionHelper::handleException)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postBlUsersSettingsMyprofilePassword(String userAgent, String xForwardedFor, UpdateCredentials entity,
                                                   Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                                   Context vertxContext) {
    try {
      vertxContext.runOnContext(c -> {
        OkapiConnectionParams connectionParams = new OkapiConnectionParams(
          okapiHeaders.get(OKAPI_URL_HEADER),
          okapiHeaders.get(OKAPI_TENANT_HEADER),
          okapiHeaders.get(OKAPI_TOKEN_HEADER));
        userPasswordService.validateNewPassword(entity.getUserId(), entity.getNewPassword(), JsonObject.mapFrom(connectionParams), h -> {
          if (h.failed()) {
            logger.error("Error during validate new user's password", h.cause());
            Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
              .respond500WithTextPlain("Internal server error during validate new user's password"));
          } else {
            Errors errors = h.result().mapTo(Errors.class);
            if (errors.getTotalRecords() == 0) {
              Map<String, String> requestHeaders = new CaseInsensitiveMap<>(okapiHeaders);
              Optional.ofNullable(userAgent)
                .ifPresent(header -> requestHeaders.put(HttpHeaders.USER_AGENT, header));
              Optional.ofNullable(xForwardedFor)
                .ifPresent(header -> requestHeaders.put(X_FORWARDED_FOR_HEADER, header));

              userPasswordService.updateUserCredential(JsonObject.mapFrom(entity), JsonObject.mapFrom(requestHeaders), r -> {
                if (r.failed()) {
                  asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                    .respond500WithTextPlain(r.cause().getMessage())));
                } else {
                  if (r.result().equals(401)) {
                    asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                      .respond401WithTextPlain("Invalid credentials")));
                  } else {
                    asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                      .respond204WithTextPlain("User's password was successfully updated")));
                  }
                }
              });
            } else {
              asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
                .respond400WithApplicationJson(errors)));
            }
          }
        });
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.succeededFuture(PostBlUsersSettingsMyprofilePasswordResponse
        .respond500WithTextPlain("Internal server error during change user's password")));
    }
  }

  @Override
  public void postBlUsersPasswordResetLink(GenerateLinkRequest entity,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                           Context vertxContext) {
    passwordResetLinkService.sendPasswordResetLink(entity.getUserId(), okapiHeaders)
      .map(link ->
        PostBlUsersPasswordResetLinkResponse.respond200WithApplicationJson(
          new GenerateLinkResponse().withLink(link)))
      .map(javax.ws.rs.core.Response.class::cast)
      .otherwise(ExceptionHelper::handleException)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postBlUsersPasswordResetReset(String userAgent, String xForwardedFor, PasswordReset entity,
                                            Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                            Context vertxContext) {
    JsonObject request = JsonObject.mapFrom(entity);
    Map<String, String> requestHeaders = new CaseInsensitiveMap<>(okapiHeaders);
    Optional.ofNullable(userAgent)
      .ifPresent(header -> requestHeaders.put(HttpHeaders.USER_AGENT, header));
    Optional.ofNullable(xForwardedFor)
      .ifPresent(header -> requestHeaders.put(X_FORWARDED_FOR_HEADER, header));

    passwordResetLinkService.resetPassword(request.getString("newPassword"), requestHeaders)
      .map(PostBlUsersPasswordResetResetResponse.respond204())
      .map(javax.ws.rs.core.Response.class::cast)
      .otherwise(ExceptionHelper::handleException)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postBlUsersPasswordResetValidate(Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler,
                                               Context vertxContext) {
    OkapiConnectionParams connectionParams = new OkapiConnectionParams(okapiHeaders.get(BLUsersAPI.OKAPI_URL_HEADER),
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT),
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TOKEN));

    passwordResetLinkService.validateLink(connectionParams)
      .map(PostBlUsersPasswordResetValidateResponse.respond204())
      .map(javax.ws.rs.core.Response.class::cast)
      .otherwise(ExceptionHelper::handleException)
      .onComplete(asyncResultHandler);
  }
}
