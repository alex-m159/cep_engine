package cep

import java.io.PrintWriter
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import cep.core.query._
import org.apache.kafka.common.TopicPartition

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import io.circe.parser._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.generic.semiauto._

import scala.io.Source
import scala.util.{Failure, Success, Try}

case class StoredQuery(
  event_state: EventStore.EventStoreState,
  query_plan: CEPQuery,
  query_id: Int,
  kafka_offsets: Map[String, Long]
)

package object core {



  trait DataSource{
    type Offset = Long
    def fetch(): Seq[Record]
  }

  trait DataSink {
    def send(data: String): Unit
  }

  // Query constructs
  case class EventTypeDef(event_name: String, field_names: Vector[FieldName])
  case class SeqComponent(event_type: String, name: String, negated: Boolean, order: Byte)
  case class SequenceDef(events: Vector[SeqComponent])

  // Data constructs
  type FieldName = String
  type EpochSeconds = Long
  sealed abstract class EventRecord
  // bind_name is the bind_name of the event that this field is associated with
  case class BoundRecordField(bind_name: String, field_name: String, field_value: Long)
  case class UnboundRecordField(field_name: String, field_value: Long)
  case class ExternalRecord(event_type: String, fields: Vector[UnboundRecordField]) extends EventRecord
  case class Record(event_type: String, timestamp: EpochSeconds, fields: Vector[UnboundRecordField], offset: Long) extends EventRecord
  case class MatchedRecord(event_type: String, bind_name: String, fields: Vector[BoundRecordField], timestamp: EpochSeconds, offset: Long)
  // this is a bit of a hack, but we need a way to identify and access the WHERE clause predicates that
  // the match was positive for in the selection operator, so that we can check the negative part of the
  // predicates/expressions in the negation operator
  case class SequenceMatch(events: Vector[MatchedRecord], matched_pos_exprs: Option[Seq[SplitConjunction]]) extends EventRecord
  case class CompositeEvent(event_type: String, bind_name: String, fields: Vector[BoundRecordField])
  case class Composite(events: Seq[CompositeEvent], timestamp: Long) extends EventRecord



  // Predicate constructs
  type Literal = Long
  case class PredField(event_name: String, field_name: String)
  abstract class Operator{
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])]
  }
  abstract class RootOperator extends Operator {
    def process(): Vector[(EventRecord, Vector[Record])]
  }

  abstract class NonRootOperator extends Operator {
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])]
  }

  case class SSC(event_types: Set[EventTypeDef], subseq: SequenceDef, source: DataSource, es: EventStore, child: Operator) extends RootOperator {
        def process(): Vector[(EventRecord, Vector[Record])] = {
          val recs = source.fetch()
          val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()
          for(e <- recs){
            es.add_event(e) match {
              case Some(matches) =>
                println("[SSC] sequence match")
                matches.foreach(m => {
                  result.append( (m, es.stream) )
                })
              case None =>
            }
          }
          result.toVector.flatMap { case (matched_rec, stream) => child.process(matched_rec, stream)}
        }

    override def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      // this is only to fulfill the Operator interface
      Vector()
    }
  }
  // expr is like a tree of boolean operators and predicates.
  // The predicates are the leaf nodes and the boolean operators are the inner nodes that connect them.
  // The expr that will be bound here is going to be the root of an Expr tree
  case class Selection(exprs: List[SplitConjunction], child: Operator) extends Operator {
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
        println("[Selection] starting selection")
        val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()
        rec match {
          case r if exprs.isEmpty =>
            result.append( (r, stream) )
          case SequenceMatch(seq, _) => {
            val true_exprs = exprs.filter { e =>
              e.pos match {
                case Some(pos_expr) =>
                  evalExpr(pos_expr, SequenceMatch(seq, None))
                case None =>
                  // wse return true since the lack of a positive side in this split
                  // means that there's no restriction from the positive-only
                  // predicates (which is what the selection operator checks), it does
                  // NOT mean that the entire expression will evaluate
                  // to true in the end; we must wait for any potential negative
                  true
              }

            }
            if (true_exprs.nonEmpty) {
              println("[Selection] match passes filter")
              result.append( (SequenceMatch(seq, Some(true_exprs)), stream) )
            }
          }
          case _ => ()
        }
      result.toVector.flatMap { case (matched_rec, stream) => child.process(matched_rec, stream)}
    }

  }
  case class Window(unit: WithinUnits.TimeUnit, magnitude: Int, child: Operator) extends Operator {
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()

      val window_size = unit match {
        case WithinUnits.SECOND => Duration(magnitude, TimeUnit.SECONDS)
        case WithinUnits.MINUTE => Duration(magnitude, TimeUnit.MINUTES)
        case WithinUnits.HOUR => Duration(magnitude, TimeUnit.HOURS)
        case WithinUnits.DAY => Duration(magnitude, TimeUnit.DAYS)
        case WithinUnits.ZERO => Duration.Inf
      }

      rec match {
        case s@SequenceMatch(events, _) =>
          val first = events.head
          val last = events.last
          /* TODO: Parameterize the time unit on this. If the timestamp is coming from Kafka then it's in
          milliseconds, if it's an epoch timestamp then it's most likely in seconds */
          val time_diff = Duration(Math.abs(Math.subtractExact(last.timestamp, first.timestamp)), TimeUnit.SECONDS)

          if (time_diff <= window_size) {
            println("[Window] match is within window")
            result.append( (s, stream) )
          }

        case _ => ()
      }

      result.toVector.flatMap { case (matched_rec, stream) => child.process(matched_rec, stream)}
    }
  }
  /* TODO: Let the negation function on only one negative event type per class instance */
  case class Negation(seq: SequenceDef, subseq: SequenceDef, neg_exprs: List[SplitConjunction], child: Operator) extends Operator {
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()

      rec match {
        case sm@SequenceMatch(events, _) => {

          val sorted = seq.events.sortBy(_.order)
          val negative_comps = sorted.zipWithIndex.filter(_._1.negated)
          if (negative_comps.isEmpty) {
            println("[Negation] no negative components")
            result.append( (sm, stream) )
          } else {
            negative_comps.foreach {

              case (negated_type, n) if 0 < n && n < seq.events.length => {
                val neg_comp = negated_type
                val left_ind = sorted.lastIndexWhere(e => !e.negated && e.order < negated_type.order)
                val left_comp = seq.events(left_ind)
                val right_ind = sorted.indexWhere(e => !e.negated && e.order > negated_type.order)
                val right_comp = seq.events(right_ind)
                //            println("first case")
                val matched_left_comp = events.find(_.bind_name == left_comp.name).get
                val matched_right_comp = events.find(_.bind_name == right_comp.name).get
                val potential_neg_comp_matches = stream
                  .filter(r => r.event_type == neg_comp.event_type)
                  .filter(r => matched_left_comp.offset < r.offset && r.offset < matched_right_comp.offset)
                /*
                   There should be a more efficient way to do this since we only need to get one positive
                   conjunction - negative conjunction pair to match.

                   It's easy to see how to do it in a "normal" style of programming: if a positive conjunction is true,
                   just check the negative right after, and then "return" that record. But I think I'd have to account
                   for the case when multiple pos-neg conjunction pairs have true positive conjunctions, so it's less of a
                   benefit than it seems at first.
                */
                val negative_component_match_exists = potential_neg_comp_matches.exists { nc =>
                  val buf = events.toBuffer
                  val bound_fields = nc.fields.map(ubf => BoundRecordField(neg_comp.name, ubf.field_name, ubf.field_value))
                  buf.insert(n, MatchedRecord(nc.event_type, neg_comp.name, bound_fields, nc.timestamp, nc.offset))
                  val found_matching_neg_event = neg_exprs.exists(ne => evalExpr(ne.neg.getOrElse(TruePred), SequenceMatch(buf.toVector, Some(List(ne)))))
                  found_matching_neg_event
                }
                /* Only yield the match if there is NO event matching the predicate */
                if(!negative_component_match_exists || neg_exprs.isEmpty) {
                  println("[Negation] match passes negation operator")
                  result.append((sm, stream))
                }
              }
              case (negated_type, n) => {
                // Won't process negation that's outside of sequence
                //            println("second case")
                result.append( (sm, stream) )
              }
              case _ => {
                // there's no negated sequence component to check
                println("third case")
                result.append( (sm, stream) )
              }
            }
          }

        }
        case _ => ()
      }

      result.toVector.flatMap { case (matched_rec, stream) => child.process(matched_rec, stream)}
    }
  }
  case class Transformation(child: Operator) extends Operator {

    def toComposite(rec: SequenceMatch): Composite = {
      val SequenceMatch(events: Vector[MatchedRecord], matched_pos_exprs: Option[Seq[SplitConjunction]]) = rec
      Composite(events.map {
        e =>
          val MatchedRecord(event_type: String, bind_name: String, fields: Vector[BoundRecordField], timestamp: EpochSeconds, offset: Long) = e
          CompositeEvent(event_type, bind_name, fields)
      }, java.time.Instant.now().getEpochSecond)
    }

    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()
      rec match {
        case Record(event_type, timestamp, fields, offset) =>
          println("[Transformation] match passes through transform")
          result.append( (rec, stream) )
        case s@SequenceMatch(events, _) =>
          println("[Transformation] match passes through transform")
          result.append( (toComposite(s), stream) )
        case c@Composite(_, _) =>
          println("[Transformation] match passes through transform")
          result.append( (c, stream) )
      }

      result.toVector.flatMap { case (matched_rec, stream) => child.process(matched_rec, stream)}
    }
  }
  case object Output extends Operator {
    def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      val result = scala.collection.mutable.ArrayBuffer[(EventRecord, Vector[Record])]()
      rec match {
        case r@Record(event_type, timestamp, fields, offset) =>
          println("[Output] match passes through output")
          result.append( (r, stream) )
        case s@SequenceMatch(events, _) =>
          println("[Output] match passes through output")
          result.append( (rec, stream) )
        case c@Composite(_, _) =>
          println("[Output] match passes through output")
          result.append( (c, stream) )
      }
      result.toVector
    }
  }

  sealed abstract class Expr

  sealed abstract class Predicate extends Expr
  sealed abstract class ParamPred extends Predicate
  sealed abstract class SimplePred extends Predicate

  // Predicate comparison operators (>, <, =, etc.)
  // Used for creating predicates
  case object TruePred                                  extends   Predicate
  case class PEq(left: PredField, right: PredField)     extends   ParamPred
  case class PGte(left: PredField, right: PredField)    extends   ParamPred
  case class PLte(left: PredField, right: PredField)    extends   ParamPred
  case class PGt(left: PredField, right: PredField)     extends   ParamPred
  case class PLt(left: PredField, right: PredField)     extends   ParamPred

  case class SEq(left: PredField, right: Literal)       extends   SimplePred
  case class SGte(left: PredField, right: Literal)      extends   SimplePred
  case class SLte(left: PredField, right: Literal)      extends   SimplePred
  case class SGt(left: PredField, right: Literal)       extends   SimplePred
  case class SLt(left: PredField, right: Literal)       extends   SimplePred

  // Predicate boolean operators
  // Used for combining predicate expressions into larger expressions
  sealed abstract class BooleanOp         extends Expr
  case class And(left: Expr, right: Expr) extends BooleanOp
  case class Or(left: Expr, right: Expr)  extends BooleanOp
  case class Not(expr: Expr)              extends BooleanOp
  case object True                        extends BooleanOp

  object WithinUnits extends Enumeration {
    type TimeUnit = Value
    val SECOND, MINUTE, HOUR, DAY, ZERO = Value
  }


  def containsNegatedComponent(p: Predicate, negativeComps: Set[SeqComponent]): Boolean = {
    p match {
      case PEq(PredField(event_name1, field_name1), PredField(event_name2, field_name2)) =>
        negativeComps.exists(e => e.name == event_name1 || e.name == event_name2)
      case PGte(PredField(event_name1, field_name1), PredField(event_name2, field_name2)) =>
        negativeComps.exists(e => e.name == event_name1 || e.name == event_name2)
      case PLte(PredField(event_name1, field_name1), PredField(event_name2, field_name2)) =>
        negativeComps.exists(e => e.name == event_name1 || e.name == event_name2)
      case PGt(PredField(event_name1, field_name1), PredField(event_name2, field_name2)) =>
        negativeComps.exists(e => e.name == event_name1 || e.name == event_name2)
      case PLt(PredField(event_name1, field_name1), PredField(event_name2, field_name2)) =>
        negativeComps.exists(e => e.name == event_name1 || e.name == event_name2)

      case SEq(PredField(event_name, field_name), l: Literal) =>
        negativeComps.exists(e => e.name == event_name)
      case SGte(PredField(event_name, field_name), l: Literal) =>
        negativeComps.exists(e => e.name == event_name)
      case SLte(PredField(event_name, field_name), l: Literal) =>
        negativeComps.exists(e => e.name == event_name)
      case SGt(PredField(event_name, field_name), l: Literal) =>
        negativeComps.exists(e => e.name == event_name)
      case SLt(PredField(event_name, field_name), l: Literal) =>
        negativeComps.exists(e => e.name == event_name)
      case TruePred => false
    }
  }


  case class SplitConjunction(pos: Option[Expr], neg: Option[Expr])

  /**
    * [[split()]] will split
    * @param expr
    * @param negativeComponents
    * @return
    */
  def split(expr: Expr, negativeComponents: Set[SeqComponent]): SplitConjunction = {
    expr match {
      case p: Predicate =>
        if(containsNegatedComponent(p, negativeComponents))
          SplitConjunction(None, Some(p))
        else
          SplitConjunction(Some(p), None)
      case a@And(left, right) =>
        val (l, r) = (left, right) match {
          case (Not(l: Predicate), Not(r: Predicate)) =>
            (l, r)
          case (l: Predicate, Not(r: Predicate)) =>
            (l, r)
          case (Not(l: Predicate), r: Predicate) =>
            (l, r)
          case (l: Predicate, r: Predicate) =>
            (l, r)
        }
        if(containsNegatedComponent(l, negativeComponents) || containsNegatedComponent(r, negativeComponents))
          SplitConjunction(None, Some(a))
        else
          SplitConjunction(Some(a), None)
      case Or(left, right) =>
        val SplitConjunction(pos1, neg1) = split(left, negativeComponents)
        val SplitConjunction(pos2, neg2) = split(right, negativeComponents)
        val neg = (neg1, neg2) match {
          case (None, None) => None
          case (Some(e), None) => Some(e)
          case (None, Some(e)) => Some(e)
          case (Some(e1), Some(e2)) => Some(Or(e1, e2))
        }
        val pos = (pos1, pos2) match {
          case (None, None) => None
          case (Some(e), None) => Some(e)
          case (None, Some(e)) => Some(e)
          case (Some(e1), Some(e2)) => Some(Or(e1, e2))
        }
        SplitConjunction(pos, neg)
    }
  }

  def split2(expr: Expr, negativeComponents: Set[SeqComponent]): Seq[SplitConjunction] = {
    expr match {
      case p: Predicate =>
        if(containsNegatedComponent(p, negativeComponents))
          Seq(SplitConjunction(None, Some(p)))
        else
          Seq(SplitConjunction(Some(p), None))
      case n@Not(p: Predicate) =>
        if(containsNegatedComponent(p, negativeComponents))
          Seq(SplitConjunction(None, Some(n)))
        else
          Seq(SplitConjunction(Some(n), None))
      case True =>
        Seq()
      case a@And(left, right) =>
        val (l, r) = (left, right) match {
          case (Not(l: Predicate), Not(r: Predicate)) =>
            (l, r)
          case (l: Predicate, Not(r: Predicate)) =>
            (l, r)
          case (Not(l: Predicate), r: Predicate) =>
            (l, r)
          case (l: Predicate, r: Predicate) =>
            (l, r)
        }

        if(containsNegatedComponent(l, negativeComponents) && containsNegatedComponent(r, negativeComponents))
          Seq(SplitConjunction(None, Some(a)))
        else if(containsNegatedComponent(l, negativeComponents) && !containsNegatedComponent(r, negativeComponents))
          Seq(SplitConjunction(Some(right), Some(left)))
        else if(!containsNegatedComponent(l, negativeComponents) && containsNegatedComponent(r, negativeComponents))
          Seq(SplitConjunction(Some(left), Some(right)))
        else
          Seq(SplitConjunction(Some(a), None))
      case Or(left, right) =>
        val left_split = split2(left, negativeComponents)
        val right_split = split2(right, negativeComponents)
        left_split ++ right_split
    }
  }

  def containsOR(expr: Expr): Boolean = {
    expr match {
      case And(left, right) =>
        containsOR(left) || containsOR(right)
      case Or(left, right) => true
      case Not(subexpr) => containsOR(subexpr)
      case _ => false
    }
  }

  def NOTOnlyOnPredicate(expr: Expr): Boolean = {
    expr match {
      case Not(subexpr) =>
        subexpr match {
          case p: Predicate => true
          case op: BooleanOp => false
        }
      case And(left, right) =>
        NOTOnlyOnPredicate(left) && NOTOnlyOnPredicate(right)
      case Or(left, right) =>
        NOTOnlyOnPredicate(left) && NOTOnlyOnPredicate(right)
      case predicate: Predicate => true
    }
  }

  def isDNF(expr: Expr): Boolean = {
    expr match {
      case And(left, right) => {
        !containsOR(left) && !containsOR(right) && NOTOnlyOnPredicate(expr)
      }
      case Or(left, right) => {
        isDNF(left) && isDNF(right) && NOTOnlyOnPredicate(expr)
      }
      case Not(subexpr) =>
        NOTOnlyOnPredicate(expr)
      // I don't know if this is in the formal definition of DNF, but
      // we're defining just the True atom to be in DNF.
      case True => true
      case p: Predicate => true
    }
  }

  // must unit test and handle negatives...which is actually the whole point
  def subExprToDNF(expr: Expr): Expr = {
    if(isDNF(expr)){
      return expr
    }

    expr match {
      case And(left1, r@Or(left, right)) =>
        Or(And(left1, left), And(left1, right))
      case And(r@Or(left, right), and_right) =>
        Or(And(left, and_right), And(right, and_right))
      case And(left, right) =>
        And(toDNF(left), toDNF(right))
      case Or(left, right) =>
        Or(toDNF(left), toDNF(right))
      case Not(subexpr: Predicate) =>
        Not(subexpr)
      case Not(subexpr: BooleanOp) =>
        subexpr match {
          case And(left, right) =>
            Or(Not(left), Not(right))
          case Or(left, right) =>
            And(Not(left), Not(right))
          case Not(expr) =>
            expr // double negation elimination
          case True =>
            Not(True) // don't change since NOT is allowed on boolean literals
        }
    }
  }


  def toDNF(expr: Expr): Expr = {
    var dnfExpr = expr
    while(!isDNF(dnfExpr)){
      dnfExpr = subExprToDNF(dnfExpr)
    }
    dnfExpr
  }

  /**
    * Should accept an expression tree in disjunctive normal form and
    * return a list of the conjunctions (AND's) from that tree.
    * Mental debugging note: it is possible for expressions to be trees containing
    * multiple AND operators, but it should contain ONLY AND operators.
    * @param expr
    * @return
    */
  def getConjunctionList(expr: Expr): List[Expr] = {
    if(!isDNF(expr))
      throw new Exception("getConjunctionList expression must be in DNF")
    def rec(expr: Expr): List[Expr] = {
      expr match {
        case p: Predicate =>
          List(p)
//          throw new RuntimeException("Reached predicate case in conjunction list aggregation")
        case a@And(left, right) => List(a)
        case o@Or(left, right) => getConjunctionList(left) ++ getConjunctionList(right)
        case n@Not(p) =>
          List(n)
        case True =>
          List()
      }
    }
    rec(expr)
  }


  private def toExprTree(root: WhereExpr): Expr = {

    root match {
      case SimplePredicate(op, left_var, left_field, literal) =>
        op match {
          case "s_eq"   => SEq(PredField(left_var, left_field), literal: Literal)
          case "s_ne"   => Not(SEq(PredField(left_var, left_field), literal: Literal))
          case "s_gte"  => SGte(PredField(left_var, left_field), literal: Literal)
          case "s_lte"  => SLte(PredField(left_var, left_field), literal: Literal)
          case "s_lt"   => SLt(PredField(left_var, left_field), literal: Literal)
          case "s_gt"   => SGt(PredField(left_var, left_field), literal: Literal)
          case _        => throw new Exception(s"operation ${op} is not accepted")
        }
      case ParamPredicate(op, left_var, left_field, right_var, right_field) =>
        op match {
          case "p_eq"   => PEq(PredField(left_var, left_field), PredField(right_var, right_field))
          case "p_ne"   => Not(PEq(PredField(left_var, left_field), PredField(right_var, right_field)))
          case "p_gte"  => PGte(PredField(left_var, left_field), PredField(right_var, right_field))
          case "p_lte"  => PLte(PredField(left_var, left_field), PredField(right_var, right_field))
          case "p_gt"   => PGt(PredField(left_var, left_field), PredField(right_var, right_field))
          case "p_lt"   => PLt(PredField(left_var, left_field), PredField(right_var, right_field))
          case _        => throw new Exception(s"operation ${op} is not accepted")
        }
      case cep.core.query.And(op, left, right) =>
        And(toExprTree(left), toExprTree(right))
      case cep.core.query.Or(op, left, right) =>
        Or(toExprTree(left), toExprTree(right))
    }
  }

  def toExprTree(root: Option[Where]): Expr = {
    root match {
      case Some(Where(real_root)) => toExprTree(real_root)
      case None => True
    }
  }







  trait PersistenceManager {
    def load(): Option[StoredQuery]
    def loadEventStoreState(): Option[EventStore.EventStoreState]
    def loadOffsets(): Option[Map[TopicPartition, Long]]
    def loadQuery(): Option[CEPQuery]
    def loadQueryId(): Option[Int]
    def store(state: EventStore.EventStoreState, offset: Long): Unit
    def store(state: StoredQuery): Unit
  }


  import java.io.PrintWriter

  object PersistenceManager {
    def parse(s: String): Option[StoredQuery] = {
      decode[StoredQuery](s) match {
        case Right(storedQuery: StoredQuery) =>
          Some(storedQuery)
        case Left(err) =>
          err.printStackTrace()
          None
      }
    }

    def remove(query_id: Int): Unit = {
      val filePath = s"query_data_${query_id}"
      val file = new java.io.File(filePath)
      if(file.exists())
        file.delete()
    }
  }

  class FilePM(q: CEPQuery, query_id: Int) extends PersistenceManager {
    private val filePath: String =
      s"query_data_${query_id}"

    private var cached: Option[StoredQuery] = None

    override def load(): Option[StoredQuery] = {
      cached match {
        case Some(value) => Some(value)
        case None => {
          val file_exists = new java.io.File(filePath).exists
          if (file_exists) {
            val file = Source.fromFile(filePath)
            val contents = file.mkString
            println(s"Restored state for Query ID ${query_id} from ${filePath}")
            decode[StoredQuery](contents) match {
              case Right(storedQuery: StoredQuery) =>
                Some(storedQuery)
              case Left(err) =>
                err.printStackTrace()
                None
            }
          } else {
            None
          }
        }
      }
    }

    override def loadEventStoreState(): Option[(ListBuffer[Record], mutable.Map[String, (Int, ListBuffer[StackItem])])] =
      load().map(_.event_state)

    override def loadQuery(): Option[CEPQuery] =
      load().map(_.query_plan)

    override def loadOffsets(): Option[Map[TopicPartition, Long]] =
      load().map(_.kafka_offsets.map { case (k, v) => (new TopicPartition(k, 0), v) } )

    override def loadQueryId(): Option[Int] =
      load().map(_.query_id)

    override def store(
      state: (ListBuffer[Record], mutable.Map[String, (Int, ListBuffer[StackItem])]),
      offset: Long): Unit = {
      val to_store = StoredQuery(state, q, query_id, Map(s"query_input_${query_id}" -> offset))
      store(to_store)
    }

    override def store(state: StoredQuery): Unit = {
      // Make sure that there's no syncronization
      // issues with reading old state before the write op is complete
      cached.synchronized {
        val json = state.asJson.noSpaces
        println(s"STORING: ${json}")
        new PrintWriter(filePath) { write(json); close }
        println(s"Stored state for Query ID ${query_id} to ${filePath}")
        cached = Some(state)
      }
    }
  }

  class MemoryPM(q: CEPQuery, query_id: Int) extends PersistenceManager {

    private var data: Option[StoredQuery] = None

    override def load(): Option[StoredQuery] =
      data

    override def loadOffsets(): Option[Map[TopicPartition, Long]] =
      load().map(_.kafka_offsets.map { case (k, v) => (new TopicPartition(k, 0), v) } )

    override def loadQuery(): Option[CEPQuery] =
      load().map(_.query_plan)

    override def loadQueryId(): Option[Int] =
      load().map(_.query_id)

    override def loadEventStoreState(): Option[(ListBuffer[Record], mutable.Map[String, (Int, ListBuffer[StackItem])])] =
      load().map(_.event_state)

    override def store(state: (ListBuffer[Record], mutable.Map[String, (Int, ListBuffer[StackItem])]), offset: Long): Unit = {
      val to_store =
        StoredQuery(
          state,
          q,
          query_id,
          Map("input_events" -> 0)
        )
      store(to_store)
    }

    override def store(state: StoredQuery): Unit = {
      data.synchronized {
        data = Some(state)
      }
    }
  }

  /**
    *  This function takes the CEPQuery and returns a tree of operators.
    * @param q
    * @return
    */
  def toAST(q: CEPQuery): (Int, PersistenceManager) => RootOperator = {
    (query_id: Int, perma: PersistenceManager) => {
      val event_defs = q.event_types.map(e => EventTypeDef(e.name, e.fields.toVector)).toSet

      // defines event sequence that we're looking for
      val event_seq = SequenceDef(q.query
        .event_clause
        .event_seq
        .map(e => SeqComponent(e.event_type, e.name, e.negated, e.order))
        .sortBy(_.order).toVector)
      // is a sub-sequence consisting of only positive events
      val event_subseq =
        SequenceDef(event_seq.events.filterNot(_.negated).sortBy(_.order))

      val neg_components = event_seq.events.filter(_.negated).toSet
      val expr_tree = toExprTree(q.query.where)
      val dnf = toDNF(expr_tree)
      val list_of_conjunctions = getConjunctionList(dnf)
      val split_conjunctions = split2(dnf, neg_components).toList


      /* TRANSFORMATION OPERATOR */
      val transformation = Transformation(Output)

      var root: Operator = transformation

      /* NEGATION OPERATOR */
      root = if (event_seq.events.toSet.diff(event_subseq.events.toSet).nonEmpty) {
        Negation(event_seq, event_subseq, split_conjunctions, root)
      } else {
        root
      }

      /* WINDOW OPERATOR */
      val (unit, magnitude) = q.query.within match {
        case Some(Within("SECOND", mag)) => (WithinUnits.SECOND, mag)
        case Some(Within("MINUTE", mag)) => (WithinUnits.MINUTE, mag)
        case Some(Within("HOURS", mag)) => (WithinUnits.HOUR, mag)
        case Some(Within("DAY", mag)) => (WithinUnits.DAY, mag)
        case Some(Within("ZERO", mag)) => (WithinUnits.ZERO, mag)
        case Some(Within(unit, mag)) => {
          throw new RuntimeException(s"Accepted WITHIN clause time units are ${WithinUnits.values.toList.mkString}")
        }
        case None => (WithinUnits.ZERO, 0)
      }

      root = if (unit != WithinUnits.ZERO) {
        Window(unit, magnitude, root)
      } else {
        root
      }

      /* SELECTION OPERATOR */

      root = if (split_conjunctions.nonEmpty) {
        Selection(split_conjunctions, root)
      } else {
        root
      }

      /* SEQUENCE SCAN AND CONSTRUCTION OPERATOR */
      val data_source = new KafkaSource(List(s"query_input_${query_id}"), query_id, perma.loadOffsets().getOrElse(Map.empty))
      val event_store = new EventStore(event_subseq, perma)
      val ssc = SSC(event_defs, event_subseq, data_source, event_store, root)

      ssc
    }
  }

  def execute(o: RootOperator): LazyList[EventRecord] = {
//    val ll = LazyList.empty
//
//
//
//    new Thread {
//      override def run(): Unit = {
//        while(true) {
//          val recs = o.process()
//          for( r <- recs ){
//            println("[execute] records appended to stream/lazylist")
//            ll.appended(r)
//          }
//        }
//      }
//    }.run()
//
//    ll
    LazyList.continually(o.process().map(_._1)).flatten
  }


  private def evalParamPred(left_field: PredField, right_field: PredField, events: SequenceMatch)(op: (Long, Long) => Boolean): Boolean = {
    events.events.find(e => e.bind_name == left_field.event_name) match {
      case Some(left_event) => {
        events.events.find(e => e.event_type == right_field.event_name) match {
          case Some(right_event) => {
            val left_field_value =
              left_event.fields.find(f => f.field_name == left_field.field_name).get
            val right_field_value =
              right_event.fields.find(f => f.field_name == right_field.field_name).get

            op(left_field_value.field_value, right_field_value.field_value)
          }
          case None => false
        }
      }
      case None => false
    }
  }

  private def evalSimplePred(left_field: PredField, right_literal: Literal, events: SequenceMatch)(op: (Long, Literal) => Boolean): Boolean = {
    events.events.find(e => e.bind_name == left_field.event_name) match {
      case Some(left_event) => {
        val left_field_value =
          left_event.fields.find(f => f.field_name == left_field.field_name).get
        op(left_field_value.field_value, right_literal)
      }
      case None => false
    }
  }

  def evalExpr(expr: Expr, events: SequenceMatch): Boolean = {
    expr match {
      case p: Predicate =>
        p match {
          case _p: ParamPred => {
            _p match {
              case PEq(left_field, right_field) =>
                evalParamPred(left_field, right_field, events)((l, r) => l == r)
              case PGte(left_field, right_field) =>
                evalParamPred(left_field, right_field, events)((l, r) => l >= r)
              case PLte(left_field, right_field) =>
                evalParamPred(left_field, right_field, events)((l, r) => l <= r)
              case PGt(left_field, right_field) =>
                evalParamPred(left_field, right_field, events)((l, r) => l > r)
              case PLt(left_field, right_field) =>
                evalParamPred(left_field, right_field, events)((l, r) => l < r)
            }
          }
          case _s: SimplePred => {
            _s match {
              case SEq(left, right) =>
                evalSimplePred(left, right, events)( (l, r) => l == r )
              case SGte(left, right) =>
                evalSimplePred(left, right, events)( (l, r) => l >= r )
              case SLte(left, right) =>
                evalSimplePred(left, right, events)( (l, r) => l <= r )
              case SGt(left, right) =>
                evalSimplePred(left, right, events)( (l, r) => l > r )
              case SLt(left, right) =>
                evalSimplePred(left, right, events)( (l, r) => l < r )
            }
          }
          case TruePred =>
            true
        }
      case b: BooleanOp =>
        b match {
          case And(left, right) =>
            evalExpr(left, events) && evalExpr(right, events)
          case Or(left, right) =>
            evalExpr(left, events) || evalExpr(right, events)
          case Not(expr) =>
            !evalExpr(expr, events)
          case True =>
            true
        }
    }
  }



}
