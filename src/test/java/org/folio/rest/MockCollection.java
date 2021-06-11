package org.folio.rest;

import io.vertx.core.json.JsonObject;

import java.util.List;

public class MockCollection {

  private List<JsonObject> objectList;
  private int totalRecords;

  public MockCollection(List<JsonObject> objectList) {
    this.objectList = objectList;
    this.totalRecords = objectList.size();
  }

  public List<JsonObject> getObjectList() {
    return objectList;
  }

  public int getTotalRecords() {
    return totalRecords;
  }

  public void setTotalRecords(int totalRecords) {
    this.totalRecords = totalRecords;
  }
}
