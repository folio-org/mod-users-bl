package org.folio.rest;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 *
 * @author kurt
 */
public class JsonStore {
  private Map<String, JsonObject> jsonMap;
  public JsonStore() {
    jsonMap = new LinkedHashMap<>();
  }

  /*
  public List<JsonObject> getCollection(Integer offset, Integer limit,
          Map<String, String> getBy) {
    if(offset == null) {
      offset = 0;
    }
    if(limit == null) {
      limit = 30;
    }
    List<JsonObject> returnList = new ArrayList<>();
    Collection<JsonObject> jsonList = jsonMap.values();
    Iterator<JsonObject> jsonIterator = jsonList.iterator();
    //fast-forward past the offset
    for( int i = 0; i < offset; i ++) {
      if(!jsonIterator.hasNext()) {
        break;
      }
      jsonIterator.next();
    }

    while(returnList.size() < limit) {
      if(!jsonIterator.hasNext()) {
        break;
      }
      JsonObject ob = jsonIterator.next();
      if(matchesCriteria(ob, getBy)) {
        returnList.add(ob);
      }
    }
    return returnList;
  }
  */
  
  public List<JsonObject> getCollection(Integer offset, Integer limit,
          QuerySet qs) {
    if(offset == null) { offset = 0; }
    if(limit == null) { limit = 30; }
    System.out.println(String.format(
            "Calling getCollection on JsonStore with parameters offset: %s, limit %s, qs %s",
            offset, limit, qs));
    List<JsonObject> rawMatchList = new ArrayList<>();
    List<JsonObject> returnList = new ArrayList<>();
    Collection<JsonObject> jsonList = jsonMap.values();
    Iterator<JsonObject> jsonIterator = jsonList.iterator();
    while(jsonIterator.hasNext()) {
      JsonObject ob = jsonIterator.next();
      if(qs == null || qs.match(ob)) {
        rawMatchList.add(ob);
      }
    }
    Iterator<JsonObject> rawMatchIterator = rawMatchList.iterator();
    //skip past the offset
    for(int i=0;i < offset && rawMatchIterator.hasNext(); i++) {
      rawMatchIterator.next();
    }
    
    while(returnList.size() < limit && rawMatchIterator.hasNext()) {
      returnList.add(rawMatchIterator.next());
    }
    
    return returnList; 
        
  }

  public JsonObject getItem(String id) {
    JsonObject returnObject;
    if(jsonMap.containsKey(id)) {
      returnObject = jsonMap.get(id);
    } else {
      returnObject = null;
    }
    return returnObject;
  }

  public JsonObject addItem(String id, JsonObject ob) throws Exception {
    if(id == null) {
      if(ob.containsKey("id")) {
        id = ob.getString("id");
      } else {
        id = UUID.randomUUID().toString();
      }
    }
    if(jsonMap.containsKey(id)) {
      throw new Exception(String.format("id '%s' already exists", id));
    }
    jsonMap.put(id, ob);
    return jsonMap.get(id);
  }

  public boolean updateItem(String id, JsonObject ob) {
    if(!jsonMap.containsKey(id)) {
      return false;
    }
    jsonMap.put(id, ob);
    return true;
  }

  public boolean deleteItem(String id) {
    if(!jsonMap.containsKey(id)) {
      return false;
    }
    jsonMap.remove(id);
    return true;
  }

  public void deleteAllitems() {
    jsonMap.clear();
  }

  private boolean matchesCriteria(JsonObject ob, Map<String, String> getBy) {
    if(getBy == null) {
      return true;
    }
    Iterator mapIterator = getBy.entrySet().iterator();
    while(mapIterator.hasNext()) {
      Map.Entry pair = (Map.Entry)mapIterator.next();
      String key = (String)pair.getKey();
      String value = (String)pair.getValue();
      if(!ob.containsKey(key) || !ob.getString(key).equals(value)) {
        return false;
      }
    }
    return true;
  }

}
