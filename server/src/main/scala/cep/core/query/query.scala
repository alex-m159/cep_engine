package cep.core

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import scala.util.Try
/**
  * This package object is meant to group together data types that will have representations
  * in the user-level DSL/query language. If it's not related to the user exposed DSL, then it
  * should go into the cep.core package.
  */
package object query {
  case class EventType(name: String, fields: List[String])
  // use a byte since it's really small and we don't need a lot of numbers
  case class EventParam(event_type: String, name: String, negated: Boolean, order: Byte)


  sealed abstract class WhereExpr
  //sealed abstract class Op
  //case object s_eq extends Op
  // leaving this as a string for now but it's possible to use a case class/case object to let
  // Circi deserialize this into the proper case object for us, then we don't have to do it
  // manually later or check with some conditional expression.
  // Just need to change the parser JSON from {"op": "s_eq", ...} to {"op": {"s_eq": {}}, ...}
  // where s_eq is a case objectdef execute(o: Operator)(yld: (EventRecord, Vector[(Int, Record)]) => Unit): Unit
  case object WhereTrue extends WhereExpr
  case class SimplePredicate(op: String, left_var: String, left_field: String, literal: Long) extends WhereExpr
  case class ParamPredicate(op: String, left_var: String, left_field: String, right_var: String, right_field: String) extends WhereExpr
  case class And(op: String, left: WhereExpr, right: WhereExpr) extends WhereExpr
  case class Or(op: String, left: WhereExpr, right: WhereExpr) extends WhereExpr

  case class EventClause(event_seq: Seq[EventParam])
  case class Where(expr_root: WhereExpr)
  case class Within(unit: String, magnitude: Int)
  case class Query(event_clause: EventClause, where: Option[Where], within: Option[Within])
  case class CEPQuery(event_types: List[EventType], query: Query)
  import scala.collection.mutable

  def parseEventTypes(json: JsonNode): List[EventType] = {
    val type_defs = json.get("event_types").elements()
    val _types = mutable.ArrayBuffer[EventType]()
    while(type_defs.hasNext) {
      val td = type_defs.next()
      val name = td.get("name").asText()
      val _fields = mutable.ArrayBuffer[String]()
      val fields_iter = td.get("fields").elements()
      while(fields_iter.hasNext) {
        val field = fields_iter.next()
        _fields.append(field.asText())
      }
      val fields = _fields.toList
      _types.append( EventType(name, fields) )
    }
    val event_types = _types.toList
    event_types
  }

  def parseEventClause(json: JsonNode): EventClause = {
    val _elems = json.get("query").get("event_clause").get("event_seq").elements()
    val _event_seq = mutable.ArrayBuffer[EventParam]()
    while (_elems.hasNext) {
      val e = _elems.next()
      val event_type = e.get("event_type").asText()
      val name = e.get("name").asText()
      val negated = e.get("negated").asBoolean()
      val order = e.get("order").asInt().toByte
      _event_seq.append(EventParam(event_type, name, negated, order))
    }
    EventClause(_event_seq.toList)
  }

  def jsonToSimple(json: JsonNode): SimplePredicate = {
    val op = json.get("op").asText()
    val left_var = json.get("left_var").asText()
    val left_field = json.get("left_field").asText()
    val literal = json.get("literal").asLong()

    SimplePredicate(op, left_var, left_field, literal)
  }

  def jsonToPredicate(json: JsonNode): ParamPredicate = {
    val op = json.get("op").asText()
    val left_var = json.get("left_var").asText()
    val left_field = json.get("left_field").asText()
    val right_var = json.get("right_var").asText()
    val right_field = json.get("right_field").asText()

    ParamPredicate(op, left_var, left_field, right_var, right_field)
  }

  def recurseWhere(tree: JsonNode): WhereExpr = {
    if(tree.has("op")) {
      val op_node = tree.get("op")
      val op_text = op_node.asText()
      op_text match {
        case "and" => {
          val left = tree.get("left")
          val right = tree.get("right")
          val left_expr = recurseWhere(left)
          val right_expr = recurseWhere(right)
          And("and", left_expr, right_expr)
        }
        case "or" => {
          val left = tree.get("left")
          val right = tree.get("right")
          val left_expr = recurseWhere(left)
          val right_expr = recurseWhere(right)
          Or("or", left_expr, right_expr)
        }
        case oper if oper.startsWith("s_") => {
          jsonToSimple(tree)
        }

        case oper if oper.startsWith("p_") => {
          jsonToPredicate(tree)
        }
        case _ =>
          throw new UnsupportedOperationException(s"Unexpected operator: ${op_text}")
      }
    } else {
      if(tree.asText() == "true") {
        WhereTrue
      } else {
        throw new UnsupportedOperationException(s"Unexpected input: ${tree}")
      }
    }

  }

  def parseWhereClause(json: JsonNode): Option[Where] = {
    val where = json.get("query").get("where")
    val root = where.get("expr_root")
    val root_op = root.get("op").asText()
    root_op match {
      case "none" => None
      case _ =>
          Some(Where(recurseWhere(root)))
    }
  }

  def parseWithinClause(json: JsonNode): Option[Within] = {
    val root = json.get("query").get("within")
    val magnitude = root.get("magnitude").asInt()
    val unit = root.get("unit").asText()
    if(magnitude == 0) None
    else Some(Within(unit, magnitude))
  }

  def fromJson(s: String): Either[String, CEPQuery] = {
    Try {
      val mapper = new ObjectMapper()
      val json = mapper.readTree(s)
      val event_types = parseEventTypes(json)
        val event_clause = parseEventClause(json)
      val where_clause = parseWhereClause(json)
      val within_clause = parseWithinClause(json)

      CEPQuery(event_types, Query(event_clause, where_clause, within_clause))
    }.fold(t => { t.printStackTrace(); Left(t.getMessage) }, query => Right(query))
  }
}