/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
    if(!ob.containsKey(field)) {
      return false;
    }
    if(operator == Operator.EQUALS) {
      if(value.equals(ob.getValue(field))) {
        return true;
      } else {
        return false;
      }
    } else if(operator == Operator.NOTEQUALS) {
      if(value.equals(ob.getValue(field))) {
        return false;
      } else {
        return true;
      }
    }
    return false;
  }

  public String toString() {
    return String.format("field: '%s', operator: '%s', value: '%s'",
            field, operator.toString(), value.toString());
  }
}
