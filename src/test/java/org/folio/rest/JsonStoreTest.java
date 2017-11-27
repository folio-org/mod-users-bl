package org.folio.rest;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author kurt
 */

public class JsonStoreTest {
  private JsonStore jsonStore;

  @Before
  public void setup() {
    jsonStore = new JsonStore();
    jsonStore.addItem(null, new JsonObject().put("id", "8fa9cdbf-528b-472b-9604-f70560fdacf4").put("name", "thing1"));
    jsonStore.addItem(null, new JsonObject().put("id", "b5197d8e-2c86-4e41-be10-d4562df524dd").put("name", "thing2"));
    jsonStore.addItem(null, new JsonObject().put("id", "f4645809-4f24-4508-a13e-a29d26e012f0").put("name", "thing3"));
    jsonStore.addItem(null, new JsonObject().put("id", "2afb0eb4-5549-47f1-af92-549b6e6005a5").put("name", "thing4"));
    jsonStore.addItem(null, new JsonObject().put("id", "5c1323eb-3ffa-48ea-8e9b-4024dc714cf3").put("name", "thing5"));
    jsonStore.addItem(null, new JsonObject().put("id", "40f50961-2836-433d-a70c-c1251cb0aa9d").put("name", "thing6"));
    jsonStore.addItem(null, new JsonObject().put("id", "83c7a936-87b3-4ac0-b2c9-03cc83c7ab5f").put("name", "thing7"));
    jsonStore.addItem(null, new JsonObject().put("id", "831460ab-3b1e-4c07-a561-78134f38e587").put("name", "thing8"));
    jsonStore.addItem(null, new JsonObject().put("id", "d0f277d1-9be7-4627-b364-f1a9e9d1a5d7").put("name", "thing9"));
    jsonStore.addItem(null, new JsonObject().put("id", "01f3c7d3-05e0-4b5c-b3b7-2534bdff60ec").put("name", "thing10"));
  }


  @Test
  public void test1() {
    assertNotNull(jsonStore.getCollection(null, null, null));
    assertTrue(jsonStore.getCollection(null, null, null).size() == 10);
    assertTrue(jsonStore.getCollection(null, null, null).get(0).getString("name").equals("thing1"));
    assertTrue(jsonStore.getItem("01f3c7d3-05e0-4b5c-b3b7-2534bdff60ec").getString("name").equals("thing10"));
  }

  @Test
  public void test2() {
    List<JsonObject> jsonList = jsonStore.getCollection(3, 1, null);
    JsonObject ob = jsonList.get(0);
    assertNotNull(ob);
    assertTrue(jsonList.size() == 1);
    assertTrue(ob.containsKey("name"));
    assertNotNull(ob.getString("name"));

    assertTrue(ob.getString("name").equals("thing4"));
  }

  @Test
  public void test3() {
    String id = "83c7a936-87b3-4ac0-b2c9-03cc83c7ab5f";
    assertNotNull(jsonStore.getItem(id));
    jsonStore.deleteItem(id);
    assertNull(jsonStore.getItem(id));
  }

  @Test
  public void test4() {
    String id = "5581c6ea-153f-46df-9c94-5a64d371a4f0";
    jsonStore.addItem(null, new JsonObject().put("id", id).put("name", "thing11"));
    JsonObject ob = jsonStore.getItem(id);
    assertTrue(ob.getString("name").equals("thing11"));
  }

 @Test
  public void test5() {
    Map getByMap = new HashMap<String, String>();
    getByMap.put("name", "thing6");
    List<JsonObject> jsonList = jsonStore.getCollection(null, 1, getByMap);
    JsonObject ob = jsonList.get(0);
    assertNotNull(ob);
    assertTrue(jsonList.size() == 1);
    assertTrue(ob.containsKey("name"));
    assertNotNull(ob.getString("name"));
    assertTrue(ob.getString("name").equals("thing6"));
  }

}
