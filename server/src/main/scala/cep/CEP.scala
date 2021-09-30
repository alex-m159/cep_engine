package cep

import java.util.Properties

import cep.core._
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.collection.mutable
import io.circe.parser._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.kafka.common.TopicPartition

case class StackItem(event: Record, stack_index: Int, prev_top_index: Int)

object EventStore {
  type EventStoreState = (mutable.ListBuffer[Record], mutable.Map[String, (Int, mutable.ListBuffer[StackItem])])
}

class EventStore(
    _subsequence: SequenceDef,
    perma: PersistenceManager){

  // just make sure it's sorted properly
  val subsequence = _subsequence.copy(events = _subsequence.events.sortBy(_.order))

  // we have to store all the event records for the selection and window operators.
  // Store with an event index/offset and then the record
  private val event_stream = perma.loadEventStoreState() match {
    case Some((saved_stream, _)) => saved_stream
    case _ => mutable.ListBuffer[Record]()
  }
  private val event_stack: mutable.Map[String, (Int, mutable.ListBuffer[StackItem])] =
    perma.loadEventStoreState() match {
    case Some((_, saved_stack)) => saved_stack
    case _ => mutable.Map()
  }
  for(s <- subsequence.events){
    event_stack.addOne(s.event_type, (s.order, mutable.ListBuffer[StackItem]()))
  }
  private val binding_map: Map[String, String] =
    (for(s <- subsequence.events) yield (s.event_type, s.name))
      .foldLeft(Map[String, String]()) { case (m, (k, v)) =>  m.updated(k, v)}
  private val terminal_stage = subsequence.events.map(_.order).max

  def get_previous_event_stack(e: Record, stacks: mutable.Map[String, (Int, mutable.ListBuffer[StackItem])]
                              ): Option[(String, (Int, mutable.ListBuffer[StackItem]))] = {

    val order = stacks(e.event_type)._1
    // Normal case were we're expecting a previous stack to exist and be populated
    stacks.filter( _._2._1 < order).maxByOption(_._2._1) match {
      case Some(value) => Some(value)
      case None => None
    }
  }

  def previous_events_present(e: Record, stacks: mutable.Map[String, (Int, mutable.ListBuffer[StackItem])]): Boolean = {
    // This is the first stack so it doesn't need a previous stack to be present
    // Given event type does not exist
    // Shouldn't happen since we pre-create stacks for all expected event types
    if(!stacks.contains(e.event_type)) {
      return false
    }

    val order = stacks(e.event_type)._1
    val first_event_stack = stacks.values.map(_._1).min
    if(order == first_event_stack) {
      return true
    }
    get_previous_event_stack(e, stacks) match {
      case Some( (event_type, (order, stack)) ) =>
        stack.nonEmpty
      case None =>
        false
    }
  }

  def add_event(event: Record): Option[Seq[SequenceMatch]] = {
    // TODO: This is a mistake. The offset indicates the order of the event and should be used to determine
    // relative order in the event stream
    event_stream.append(event.copy(offset = event_stream.size.toLong))
    if(!previous_events_present(event, event_stack)) {
      return None
    } else {
      val (order, stack) = event_stack(event.event_type)
      val top_index_of_prev_stack = if(order == 0) {
        -1
      } else {
        get_previous_event_stack(event, event_stack) match {
          case Some( (_event_type, (_order, prev_stack)) ) =>
            prev_stack.map(_.stack_index).max
          case None => -1
        }

      }
      val stack_item = StackItem(event, stack.length, top_index_of_prev_stack)
      stack.append( stack_item )
      val (_, _stack) = event_stack(event.event_type)
      assert(_stack.contains(stack_item))
      if(order == terminal_stage){

        perma.store((event_stream, event_stack), event.offset)
        Some(get_most_recent_match())
      } else {
        None
      }
    }
  }

  private def traverse_stacks(
                               stacks: mutable.Map[String, (Int, mutable.ListBuffer[StackItem])],
                               max_index: Int,
                               stack_key: String
                             ): List[List[(String, StackItem)]] = {
    val (order, stack) = stacks(stack_key)
    val event_bind_name = subsequence.events.find(_.order == order) match {
      case Some(value) => value.name
      case None => throw new Exception("Cannot bind event to varaible name in query")
    }
    val match_members =
      stack
        .takeWhile(_.stack_index <= max_index)
        .toList
    if(order > 0) {
      val next_order_key = stacks.find(_._2._1 == order-1).get._1

      for (
        m <- match_members;
        p <- traverse_stacks(stacks, m.prev_top_index, next_order_key)
      ) yield {
        (event_bind_name, m) +: p
      }
    } else {
      match_members.map(i => List( (event_bind_name, i) ))
    }
  }

  /**
    * get_most_recent_match will get only the events that match to the most recent element in the final state.
    * get_matches will return matches for all the events in the final state, resulting in matches being output
    * more than once.
    */
  private[cep] def get_most_recent_match(): Vector[SequenceMatch] = {

    // get stack for last event type in subseqeunce
    val (order, final_stack) = event_stack(subsequence.events.last.event_type)

    val preresults: List[List[(String, StackItem)]] = {
      val ending_record = final_stack.last
      val last_bind_name = subsequence.events.maxBy(_.order).name
      val prev_recs = traverse_stacks(event_stack, ending_record.prev_top_index, subsequence.events.init.last.event_type)
      for(rec <- prev_recs) yield
        (List((last_bind_name, ending_record)) ++ rec).reverse
    }
    preresults.map { result_list =>
      // have to reverse the list since the matching is returned in reverse order
      // from the stacks
      SequenceMatch(result_list.map( e => MatchedRecord(e._2.event.event_type, binding_map(e._2.event.event_type), e._2.event.fields.map(field => BoundRecordField(e._1, field.field_name, field.field_value)), e._2.event.timestamp, e._2.event.offset)).toVector, None)
    }.toVector
  }

  private def get_matches(stacks: mutable.Map[String, (Int, mutable.ListBuffer[StackItem])]): Vector[SequenceMatch] = {
    val final_state_stack = stacks.maxBy(_._2._1)._1

    val (order, final_stack) = stacks(final_state_stack)
    val prev_stack_key = stacks.find(_._2._1 == order-1).get._1
    val preresults = {for(rec <- final_stack;
                          prev_recs <- traverse_stacks(stacks, rec.prev_top_index, prev_stack_key))
      yield {
        val bind_name = subsequence.events.maxBy(_.order).name
        List((bind_name, rec)) ++ prev_recs
      }}.toList

    val results = preresults.map { result_list =>
      // have to reverse the list since the matching is returned in reverse order
      // from the stacks
      SequenceMatch(result_list.reverse.map( e => MatchedRecord(e._2.event.event_type, binding_map(e._2.event.event_type), e._2.event.fields.map(field => BoundRecordField(e._1, field.field_name, field.field_value)), e._2.event.timestamp, e._2.event.offset)).toVector, None)
    }.toVector
    if(results.nonEmpty) {
      perma.store((event_stream, event_stack), event_stream.last.offset)
    }
    results
  }

  def stream: Vector[Record] =
    event_stream.toVector
}

/**
  * The start point offset map should cover all the topics in the topics list
  * and should not include any topics that are not in the topics list
  * @param topics
  * @param query_id
  * @param start_point
  */

class KafkaSource(topics: Seq[String], query_id: Int, assign_and_seek: Map[TopicPartition, Long]) extends DataSource {

  private val props = new Properties()
  props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("bootstrap.servers", "localhost:9092")
  props.put("group.id", s"cep-group-${query_id}")
  props.put("enable.auto.commit", "true")
  props.put("auto.commit.interval.ms", "1000")
  props.put("session.timeout.ms", "30000")
  private val c = new KafkaConsumer[String, String](props)
  private val a: java.util.ArrayList[TopicPartition] =
    new java.util.ArrayList[TopicPartition]()
  for(topic <- topics){
    a.add(new TopicPartition(topic, 0))
  }
  c.assign(a)
  assign_and_seek.foreach { o =>
    c.seek(o._1, o._2)
  }

  override def fetch(): Seq[Record] = {

    val recs = c.poll(java.time.Duration.ofSeconds(5).toMillis)
    val m_records = mutable.ListBuffer[ConsumerRecord[String, String]]()
    val iter = recs.iterator()
    while(iter.hasNext){
      m_records.append(iter.next())
    }
    val records = m_records.toList

    records.foreach { r =>
      decode[ExternalRecord](r.value()) match {
        case Left(value) =>
          println(value)
        case Right(value) =>
          ()
      }
    }
    val parsedSorted = records.map(r => {
      val record = decode[ExternalRecord](r.value())
      (r.offset(), r.timestamp(), record)
    }).filter(_._3.isRight)
      .map(t => (t._1, t._2, t._3.right.get))
      .map(t => Record(t._3.event_type, t._2, t._3.fields, t._1))
      .sortBy(_.offset)
    parsedSorted
  }
}

class MockSource extends DataSource {
  private var offset = -1
  private val epochtime = java.time.Instant.now().getEpochSecond
  private val records =
    List[ExternalRecord](
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
    )
      .zipWithIndex
      .map(e => Record(e._1.event_type, epochtime, e._1.fields, e._2))
  override def fetch(): Seq[Record] = {
    offset += 1
    Seq(records(offset % records.size))
  }
}

class KafkaSink(topic: String, query_id: Int) extends DataSink {

  private val props = new Properties()
  props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("bootstrap.servers", "localhost:9092")
  props.put("enable.auto.commit", "true")
  props.put("group.id", s"group-${query_id}")
  private val p = new KafkaProducer[String, String](props)
  override def send(data: String): Unit = {
    p.send(new ProducerRecord[String, String](topic, data))
    p.flush()
  }
}
