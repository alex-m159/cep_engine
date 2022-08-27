import java.net._
import java.io._
import java.util.Properties

import cep.{KafkaSink, StoredQuery}
import cep.core.query.CEPQuery
import cep.core._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}


//import com.fasterxml.jackson.annotation.{JsonProperty, JsonValue}

import scala.concurrent.{ExecutionContext, Future}
import scala.io._
import scala.util.control.Breaks._
import io.circe.parser._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._


class Timer() {
  @noinline
  def timer[U](f: () => U): Int = {
    val t1 = System.nanoTime
    f()
    val diff = (System.nanoTime - t1)
    (diff/1000000).toInt
  }
}

class RecordGenerator(query_id: Int) extends Thread {
  override def run(): Unit = {
    println("Running test data generation thread")
    println("Sleeping for 10 seconds...")
    Thread.sleep(10000)
    
    val data = List(
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("D", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("D", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("D", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("D", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("A", Vector(UnboundRecordField("field1", 100),  UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("B", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 100), UnboundRecordField("field3", 3))),
      ExternalRecord("C", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3))),
      ExternalRecord("D", Vector(UnboundRecordField("field1", 1),    UnboundRecordField("field2", 2),   UnboundRecordField("field3", 3)))
    )
    val props = new Properties()
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("bootstrap.servers", "localhost:9092")
    props.put("group.id", "test")
    props.put("enable.auto.commit", "true")
    props.put("auto.commit.interval.ms", "1000")
    props.put("session.timeout.ms", "30000")
    val p = new KafkaProducer[String, String](props)
    println("Ok, getting started with data generation!")
    for(rec <- data) {
      val pr = new ProducerRecord[String, String](s"query_input_${query_id}", rec.asJson.noSpaces)
      println("Created new ProducerRecord")
      p.send(pr)
      println("Send the Record")
      
      // println("Flushed the producer")
      println("sent test data")
      Thread.sleep(400)
    }
    p.flush()
    println("done.")
    println("exiting thread.")


  }
}

class ClientHandler(cs: Socket) extends Runnable {
  override def run(): Unit =
    println("thread is running")
    try {
      Main.handle_client(cs)
    } finally {
      cs.close()
    }
}

object ActiveQueries {
  import scala.collection.mutable
  val running_queries: mutable.Map[Int, CEPQuery] = mutable.Map()

  def add_query(q: CEPQuery): Option[Int] = {
    val k = get_query_id(q)
    if(!running_queries.contains(k)) {
      running_queries.addOne(k, q)
      Some(k)
    } else {
      None
    }
  }

  def remove_query(q: CEPQuery) = {
    running_queries.remove(get_query_id(q))
    PersistenceManager.remove(get_query_id(q))
  }

  def remove_query(hc: Int) =
    running_queries.remove(hc)

  def get_query_id(q: CEPQuery): Int = {
    q.hashCode()
  }
}

class QueryThread(query: CEPQuery, query_id: Int, pm: Option[PersistenceManager] = None) extends Thread {
  override def run(): Unit = {
    try {
      println("Added query")
      val sink = new KafkaSink(s"output-${query_id}", query_id)
      println("Executing query plan")
      val perma = pm match {
        case Some(value) => value
        case None => new FilePM(query, query_id)
      }
      val root_op = toAST(query)
      val initialied_op = root_op(query_id, perma)
      println(s"Query Plan:\n\t${initialied_op}")
      val chan = execute(initialied_op)
      while( true ) {
        val m = chan.read
        println("sent match to sink")
        sink.send(m.asJson.noSpaces)
      }
    } finally {
//      ActiveQueries.remove_query(query)
      ()
    }
  }
}

import spark.Spark.{get, post, port, delete}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

object Main extends App {
  implicit val ec = ExecutionContext.global


  def handle_client(cs: Socket): Unit = {
    println("running client handler")

    val in = new BufferedSource(cs.getInputStream).getLines()
    println("calling foreach")
      for(line <- in) {
        println("reading line")
        println(line)
        if (line.contains("close")) {
          cs.close()
          return
        }
        if (line.contains("shutdown")) {
          cs.close()
          return
        }
        println("decoding...")

//        val cepq = decode[CEPQuery](line)
        val cepq = cep.core.query.fromJson(line)
        println(s"client ${cs.getInetAddress.toString}:${cs.getLocalPort.toString}: ${cepq}")
        cepq match {
          case Left(value) => {
            val m = Map(
              "response" -> "invalid query",
              "err" -> value
            )
            val out = cs.getOutputStream
            out.write(m.asJson.noSpaces.getBytes)
            out.flush()
            out.flush()
            out.close()
            cs.close()
            println("close client socket")
          }
          case Right(query) => {
            val m = Map(
              "response" -> "query parsed successfully"
            )
            val out = cs.getOutputStream
            out.write(m.asJson.noSpaces.getBytes)
            out.flush()
            out.flush()
//            out.close()
//            cs.close()
            Future {
              new RecordGenerator(0).run()
            }
            println("close client socket")
            throw new Exception("This code path should be unused now")
            val pm = new MemoryPM(query, 0)
            val root_operator = toAST(query)(0, pm)
            try {
              ActiveQueries.add_query(query)
              val chan = execute(root_operator)
              while(true) {
                val r = chan.read
                println(s"[QUERY MATCH]: ${r}")
                out.write(r.asJson.noSpaces.toCharArray.map(_.toByte).appended('\u0000'.toByte))
              }
            } finally {
              ()
            }

          }
        }
      }
  }

  def createQuery(json: String): Try[Int] = {
    cep.core.query.fromJson(json) match {
      case Left(err) =>
        println("Failure in createQuery")
        Failure(new Exception(err))
      case Right(query) =>
        ActiveQueries.add_query(query) match {
          case Some(query_id) =>
            val thread = new QueryThread(query, query_id)
            thread.setDaemon(true)
            thread.setName(s"query-${query.hashCode()}")
            thread.start()
            Success(query_id)
          case None =>
            Failure(new Exception("Query has identical query id already exists"))
        }


    }
  }

  def loadSaved(): Unit = {
    val dir = new File("./")
    val query_files: List[File] = dir.listFiles().filter(_.getName.startsWith("query_data")).toList
    val queries: List[StoredQuery] = query_files.flatMap { f: File =>
      val file = Source.fromFile(f.getName)
      val contents: String = file.mkString
      PersistenceManager.parse(contents)
    }
    queries.foreach { saved =>
      ActiveQueries.add_query(saved.query_plan) match {
        case Some(query_id) =>
          println(s"Restoring - ${query_id}")
          val thread = new QueryThread(saved.query_plan, saved.query_id)
          thread.setDaemon(true)
          thread.setName(s"query-${query_id}")
          thread.start()
          Success(query_id)
        case None =>
          Failure(new Exception("Query has identical query id already exists"))
      }
    }
  }

  println("Loading saved queries")
  loadSaved()

  println("Launching client server...")
  port(8000)
  get("/query", (req, res) => {
    println(req.userAgent())
    Map( "queries" -> ActiveQueries.running_queries.toMap).asJson.noSpaces
  })

  post("/query", (req, res) => {
    try {
      val query_json = req.body()
      createQuery(query_json) match {
        case Failure(exception) =>
          println(s"Failure: ${exception}")
          Map("ok" -> false).asJson.noSpaces
        case Success(query_id) =>
          println("Accepted query")
          new RecordGenerator(query_id).start()
          Map("ok" -> true).asJson.deepMerge(Map("query_id" -> query_id).asJson).noSpaces
      }
    } catch {
      case e: Exception =>
        println("Caught exception")
        e.printStackTrace()
        Map("ok" -> false).asJson.noSpaces
    }

  })

  post("/query/delete", (req, res) => {
    val req_json = req.body()
    cep.core.query.fromJson(req_json) match {
      case Left(value) => {
        Map[String, String]("ok" -> false.toString, "err" -> value).asJson.noSpaces
      }
      case Right(cep_q) => {
        ActiveQueries.remove_query(cep_q)
        Map[String, String]("ok" -> true.toString).asJson.noSpaces
      }
    }
  })

  post("/query/plan", (req, res) => {
    val query_json = req.body()
    cep.core.query.fromJson(query_json) match {
      case Left(value) =>
        Map[String, String]("ok" -> false.toString, "err" -> value).asJson.noSpaces
      case Right(query) =>
        val query_id = ActiveQueries.get_query_id(query)
        val perma = new FilePM(query, query_id)
        val uninitialized_plan = toAST(query)
        Map[String, String]("ok" -> true.toString, "plan" -> uninitialized_plan(query_id, perma).toString).asJson.noSpaces
    }
  })



}