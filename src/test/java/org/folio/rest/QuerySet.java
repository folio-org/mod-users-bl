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
enum BooleanOperator { AND, OR }

public class QuerySet {
  private Object left;
  private Object right;
  private BooleanOperator operator;


  public QuerySet setLeft(Object ob) {
    this.left = ob;
    return this;
  }
  
  public QuerySet setRight(Object ob) {
    this.right = ob;
    return this;
  }
  
  public QuerySet setOperator(BooleanOperator op) {
    this.operator = op;
    return this;
  }
  
  public Boolean match(JsonObject json) {
    Boolean leftEval;
    Boolean rightEval;
    leftEval = evalQueryObject(this.left, json);
    rightEval = evalQueryObject(this.right, json);
    if(this.operator == BooleanOperator.AND) {
      return leftEval && rightEval;
    } else if(this.operator == BooleanOperator.OR) {
      return leftEval || rightEval;
    }
    return false;
  }
  
  private static Boolean evalQueryObject(Object ob, JsonObject json) {
    if(ob instanceof Boolean) {
      return (Boolean)ob;   
    } else if(ob instanceof Query) {
      return ((Query) ob).match(json);
    } else if(ob instanceof QuerySet) {
      return ((QuerySet) ob).match(json);
    }
    return false;
  }
}