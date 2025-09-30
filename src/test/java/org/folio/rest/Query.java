package org.folio.rest;

import io.vertx.core.json.JsonObject;

/**
 *
 * @author kurt
 */
enum Operator { EQUALS, NOTEQUALS, GREATEREQUALS }

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
      checkObject = (checkObject instanceof JsonObject) ? ((JsonObject) checkObject).getValue(fld) : null;
    }

    switch (operator) {
      case EQUALS: return value.equals(checkObject);
      case NOTEQUALS: return !value.equals(checkObject);
      case GREATEREQUALS:
        if (checkObject == null) return false;
        if (checkObject instanceof Number && value instanceof Number) {
          double docValue = ((Number) checkObject).doubleValue();
          double queryValue = ((Number) value).doubleValue();
          return docValue >= queryValue;
        } else if (checkObject instanceof String && value instanceof String) {
          return ((String) checkObject).compareTo((String) value) >= 0;
        }
        return false;
      default: throw new IllegalStateException("Operator can't be null");
    }
  }

  public String toString() {
    return String.format("field: '%s', operator: '%s', value: '%s'",
            field, operator.toString(), value.toString());
  }
}
