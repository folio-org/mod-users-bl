/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.rest;

import io.vertx.core.json.JsonObject;
import org.z3950.zing.cql.CQLAndNode;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLOrNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

/**
 *
 * @author kurt
 */
enum BooleanOperator { AND, OR }

public class QuerySet {
  private Object left;
  private Object right;
  private BooleanOperator operator;

  public static QuerySet fromCQL(String query) throws CQLParseException {
    CQLParser parser = new CQLParser();
    CQLNode node = null;
    QuerySet root = null;
    try {
      node = parser.parse(query);
    } catch(CQLParseException cqlpe) {
      throw cqlpe;
    } catch(Exception e) {
      throw new UnsupportedOperationException(String.format(
              "Could not parse query %s: %s", query, e.getLocalizedMessage()));
    }
    root = new QuerySet();
    populateQuerySet(root, node);
    return root;
  }
  public String toString() {
    return String.format("(%s) %s (%s)", this.left.toString(),
            this.operator.toString(), this.right.toString());
  }

  private static QuerySet populateQuerySet(QuerySet querySet, CQLNode node) {
    System.out.println("populating queryset");
    if(node instanceof CQLTermNode) {
      CQLTermNode termNode = (CQLTermNode)node;
      System.out.println("Processing term node\n");
      Query query = new Query();
      if("==".equals(termNode.getRelation().getBase()) ||
              "=".equals(termNode.getRelation().getBase())) {
        query.setOperator(Operator.EQUALS);
      } else if("<>".equals(termNode.getRelation().getBase())) {
        query.setOperator(Operator.NOTEQUALS);
      } else {
        throw new UnsupportedOperationException("Relation '"
                + termNode.getRelation().getBase() + "' is not supported");
      }
      query.setField(termNode.getIndex());
      query.setValue(termNode.getTerm());
      querySet.setLeft(query);
      querySet.setRight(Boolean.TRUE);
      querySet.setOperator(BooleanOperator.AND);
    } else if(node instanceof CQLBooleanNode) {
      CQLBooleanNode booleanNode = (CQLBooleanNode)node;
      System.out.println("Processing boolean node\n");
      if(node instanceof CQLAndNode) {
        querySet.setOperator(BooleanOperator.AND);
      } else if(node instanceof CQLOrNode) {
        querySet.setOperator(BooleanOperator.OR);
      } else {
        throw new UnsupportedOperationException("Unsupported Boolean operation: "
                + node.getClass().getCanonicalName());
      }
      querySet.setLeft(populateQuerySet(new QuerySet(), booleanNode.getLeftOperand()));
      querySet.setRight(populateQuerySet(new QuerySet(), booleanNode.getRightOperand()));
    }
    return querySet;
  }

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
