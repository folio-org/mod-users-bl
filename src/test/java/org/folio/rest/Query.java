package org.folio.rest;

import io.vertx.core.json.JsonObject;

/**
 *
 * @author kurt
 */
enum Operator { EQUALS, NOTEQUALS }

public class Query {
  private Operator operator;
  private String field;
  private Object value;

  public Query setOperator(Operator op) {
    operator = op;
    return this;
  }

  public Query setField(String field) {
    this.field = field;
    return this;
  }

  public Query setValue(Object value) {
    this.value = value;
    return this;
  }

  public Boolean match(JsonObject ob) {
    Object checkObject = ob;
    String[] fields = field.split("\\.");

    for (String fld: fields) {
      checkObject = ((JsonObject)checkObject).getValue(fld);
    }

    switch (operator) {
      case EQUALS: return value.equals(checkObject);
      case NOTEQUALS: return !value.equals(checkObject);
      default: throw new IllegalStateException("Operator can't be null");
    }
  }

  public String toString() {
    return String.format("field: '%s', operator: '%s', value: '%s'",
            field, operator.toString(), value.toString());
  }
}
