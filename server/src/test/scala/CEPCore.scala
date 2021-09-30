package cep.test

import org.scalatest.funsuite.AnyFunSuite
import cep.core.{EventRecord, _}
import cep.{EventStore, MockSource}

class CEPCoreSuite extends AnyFunSuite {
  test("Test toDNF") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(a, d)
    val right = Or(b, c)
    val start_expr = And(left, right)


    val end_expr = Or(
      And(And(a, d), b),
      And(And(a, d), c)
    )

    assert(isDNF(start_expr) == false)
    assert(toDNF(start_expr) == end_expr)
  }

  test("Test toDNF complex") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val e = PEq(PredField("e", "id"), PredField("f", "id"))
    val f = PEq(PredField("f", "id"), PredField("g", "id"))
    val g = PEq(PredField("g", "f1"), PredField("h", "f1"))

    val h = PEq(PredField("h", "id"), PredField("i", "id"))
    val i = PEq(PredField("i", "id"), PredField("j", "id"))
    val j = PEq(PredField("j", "f1"), PredField("k", "f1"))

    val left = And(a, b)
    val right =
      And(
        c,
        Or(g,
          And(h,
            Or(i,
              j))))

    val start_expr = Or(left, right)


    val end_expr = Or(
      And(a, b),
      Or(
        And(c, g),
        Or(
          And(c, And(h, i)),
          And(c, And(h, j))
        )
      )
    )

    assert(isDNF(start_expr) == false)
    assert(toDNF(start_expr) == end_expr)
  }

  test("Test toDNF with Double Negation Elimination") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(a, d)
    val right = Or(b, c)
    val start_expr = And(Not(Not(left)), right)


    val end_expr = Or(
      And(And(a, d), b),
      And(And(a, d), c)
    )

    assert(isDNF(start_expr) == false)
    assert(toDNF(start_expr) == end_expr)
  }

  test("Test toDNF with De Morgan's Law") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(a, d)
    val right = Or(b, c)
    val start_expr = And(Not(left), right)


    val end_expr = Or(
      Or(And(Not(a), b), And(Not(d), b)),
      Or(And(Not(a), c), And(Not(d), c))
    )
    val res =  toDNF(start_expr)
    assert(isDNF(start_expr) == false)
    if(res == end_expr) {
      assert(true)
    } else {
      assert(false)
    }
  }

  test("Split gets positive and negative components from OR") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(a, d)
    val right = Or(b, c)
    val start_expr = right

    val dnf = toDNF(start_expr)
    val negComp = Set(SeqComponent("A", "a", true, 1))
    val res = split2(dnf, negComp)
    val expected = Set(SplitConjunction(Some(b), None), SplitConjunction(None, Some(c)))
    if(expected == res.toSet) {
      assert(true)
    } else {
      assert(false)
    }
  }

  test("Split gets negative components from AND") {
    val a = PEq(PredField("a", "id"), PredField("b", "id"))
    val d = PEq(PredField("b", "id"), PredField("d", "id"))
    val b = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val c = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(a, d)
    val right = Or(b, c)
    val start_expr = left

    val dnf = toDNF(start_expr)
    val negComp = Set(SeqComponent("A", "a", true, 1))
    val res = split2(dnf, negComp)
    val expected = Set(SplitConjunction(Some(d), Some(a)))

    if(expected == res.toSet) {
      assert(true)
    } else {
      assert(false)
    }
  }

  test("Split gets negative conjuctions from DNF expression") {
    val ab = PEq(PredField("a", "id"), PredField("b", "id"))
    val bd = PEq(PredField("b", "id"), PredField("d", "id"))
    val bc = PEq(PredField("b", "f1"), PredField("c", "f1"))
    val a_lit = SEq(PredField("a", "f1"), 100L: Literal)
    val left = And(ab, bd)
    val right = Or(bc, a_lit)
    val start_expr = And(Not(left), right)


    val dnf = toDNF(start_expr)
    val end_expr = Or(
      Or(And(Not(ab), bc), And(Not(bd), bc)),
      Or(And(Not(ab), a_lit), And(Not(bd), a_lit))
    )
    if(end_expr == dnf) {
      assert(true)
    } else {
      assert(false)
    }
    val negComp = Set(SeqComponent("A", "a", true, 1))
    val actual = split2(dnf, negComp)
    val expected = Set(
      SplitConjunction(Some(bc), Some(Not(ab))),
      SplitConjunction(Some(And(Not(bd),bc)), None),
      SplitConjunction(None, Some(And(Not(ab),a_lit))),
      SplitConjunction(Some(Not(bd)), Some(a_lit))
    )

    if(actual.toSet == expected) {
      assert(true)
    } else {
      assert(false)
    }
  }

  test("test SSC") {
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", false, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    import cep.{MockSource, EventStore}
    val datasource = new MockSource()
    val es = new EventStore(subseq)
    val ssc = SSC(event_defs, subseq, datasource, es, Output)
//    println(ssc.process().head)
    assert(ssc.process().isEmpty)
    assert(ssc.process().isEmpty)

    val output = ssc.process()
    assert(output.size == 1)
    assert(output.head._1.isInstanceOf[SequenceMatch])
    assert(output.head._1.asInstanceOf[SequenceMatch].events.size == 3)
    assert(output.head._1.asInstanceOf[SequenceMatch].events(0).event_type == "A")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(0).bind_name == "a")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(0).fields.size == 3)

    assert(output.head._1.asInstanceOf[SequenceMatch].events(1).event_type == "B")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(1).bind_name == "b")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(1).fields.size == 3)

    assert(output.head._1.asInstanceOf[SequenceMatch].events(2).event_type == "C")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(2).bind_name == "c")
    assert(output.head._1.asInstanceOf[SequenceMatch].events(2).fields.size == 3)

  }

  case object NullOperator extends Operator {
    def process():  Vector[(EventRecord, Vector[Record])] = {
      Vector()
    }

    override def process(rec: EventRecord, stream: Vector[Record]): Vector[(EventRecord, Vector[Record])] = {
      Vector((rec, stream))
    }
  }
  test("Selection with no expressions to check") {
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", false, 1),
          SeqComponent("C", "c", false, 2)
        )
      )

    val ssc_output =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),1619744290L,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),1619744290L,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),1619744290L,2)
          ),
          None
        ),
        Vector(
          Record("A",1619744290L,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val expected  =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),1619744290L,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),1619744290L,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),1619744290L,2)
          ),
          None
        ),
        Vector(
          Record("A",1619744290L,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )
    val sel = Selection(List(), NullOperator)
    assert(sel.process(ssc_output._1, ssc_output._2).size == 1)
    assert(sel.process(ssc_output._1, ssc_output._2).head == ssc_output)

  }

  test("Selection with expression to check") {
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", false, 1),
          SeqComponent("C", "c", false, 2)
        )
      )

    val ssc_output =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),1619744290L,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),1619744290L,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),1619744290L,2)
          ),
          None
        ),
        Vector(
          Record("A",1619744290L,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )
    val expr = SEq(PredField("b", "field1"), 1L: Literal)
    val sc = split2(toDNF(expr), Set()).toList
    val sel = Selection(sc, NullOperator)

    val expected  =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),1619744290L,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),1619744290L,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),1619744290L,2)
          ),
          Some(sc)
        ),
        Vector(
          Record("A",1619744290L,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",1619744290L,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    assert(sel.process(ssc_output._1, ssc_output._2).size == 1)
    assert(sel.process(ssc_output._1, ssc_output._2).head == expected)

  }

  test("Check evalExpr with single predicate") {
    val expr = SEq(PredField("b", "field1"), 1L: Literal)
    val seqmatch = SequenceMatch(
      Vector(
        MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),1619744290L,0),
        MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),1619744290L,1),
        MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),1619744290L,2)
      ),
      None
    )

    assert( evalExpr(expr, seqmatch) == true)
  }

  test("Events within window") {
    val base = 1619744290L

    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+10,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+10,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )
    val window = Window(WithinUnits.SECOND, 10, NullOperator)
    assert( window.process(input._1, input._2).size == 1)
  }

  test("Events outside window") {
    val base = 1619744290L

    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )
    val window = Window(WithinUnits.SECOND, 10, NullOperator)
    assert( window.process(input._1, input._2).isEmpty)
  }

  test("Negation operator lets matches pass through if no negative components and no filter exprs given") {
    val base = 1619744290L
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", false, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val negation = Negation(subseq, subseq, List(), NullOperator)
    assert( negation.process(input._1, input._2).size == 1)
  }

  test("Negation operator stops match if it finds negative element that also matches predicates") {
    val base = 1619744290L
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", true, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val negation = Negation(subseq, subseq, List(SplitConjunction(None, Some(SEq(PredField("b", "field1"), 1L: Literal)))), NullOperator)
    assert( negation.process(input._1, input._2).isEmpty)
  }

  test("Negation operator lets match pass through if it finds negative element that does not match predicates") {
    val base = 1619744290L
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", true, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val negation = Negation(subseq, subseq, List(SplitConjunction(None, Some(SEq(PredField("b", "field1"), 2L: Literal)))), NullOperator)
    assert( negation.process(input._1, input._2).size == 1)
  }

  test("Negation operator does not recheck the positive event conjunctions") {
    /*
    We're going to pass in a predicate that applies to the "positive" events in the sequence and should
    evaluate to False for the match that we give. If it was False in the Selection operator then the record should be
    filtered out, but since the Negation operator shouldn't be checking the conjunctions for the positive events,
    the Negation operator should still let this match pass through.
    */
    val base = 1619744290L
    val event_defs =
      Set(
        EventTypeDef("A", Vector("field1", "field2", "field3")),
        EventTypeDef("B", Vector("field1", "field2", "field3")),
        EventTypeDef("C", Vector("field1", "field2", "field3"))
      )
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", true, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val negation = Negation(subseq, subseq, List(SplitConjunction(Some(Not(SEq(PredField("a", "field1"), 100L: Literal))), Some(SEq(PredField("b", "field1"), 2L: Literal)))), NullOperator)
    assert( negation.process(input._1, input._2).size == 1)
  }

  test("EventStore outputs only most recent events") {
    val subseq =
      SequenceDef(
        Vector(
          SeqComponent("A", "a", false, 0),
          SeqComponent("B", "b", false, 1),
          SeqComponent("C", "c", false, 2)
        )
      )
    val event_store = new EventStore(subseq)
    val base = 1619744290L
    val input =
      (
        SequenceMatch(
          Vector(
            MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
            MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
            MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+11,2)
          ),
          None
        ),
        Vector(
          Record("A",base,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),0),
          Record("B",base+5,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),1),
          Record("C",base+11,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),2)
        )
      )

    val input2 =
      (
        Vector(
          SequenceMatch(
            Vector(
              MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
              MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+5,1),
              MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+19,5)
            ),
            None
          ),
          SequenceMatch(
            Vector(
              MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base,0),
              MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+16,4),
              MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+19,5)
            ),
            None
          ),
          SequenceMatch(
            Vector(
              MatchedRecord("A","a",Vector(BoundRecordField("A","field1",100), BoundRecordField("A","field2",2), BoundRecordField("A","field3",3)),base+15,3),
              MatchedRecord("B","b",Vector(BoundRecordField("B","field1",1), BoundRecordField("B","field2",2), BoundRecordField("B","field3",3)),base+16,4),
              MatchedRecord("C","c",Vector(BoundRecordField("C","field1",1), BoundRecordField("C","field2",2), BoundRecordField("C","field3",3)),base+19,5)
            ),
            None
          )
        ),
        Vector(
          Record("A",base+15,Vector(UnboundRecordField("field1",100), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),3),
          Record("B",base+16,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),4),
          Record("C",base+19,Vector(UnboundRecordField("field1",1), UnboundRecordField("field2",2), UnboundRecordField("field3",3)),5)
        )
      )

    assert(event_store.add_event(input._2(0)).isEmpty)
    assert(event_store.add_event(input._2(1)).isEmpty)
    assert(event_store.add_event(input._2(2)).isDefined)
    val seq_match = event_store.get_most_recent_match()
    assert(seq_match.size == 1)
    assert(seq_match.head == input._1)

    assert(event_store.add_event(input2._2(0)).isEmpty)
    assert(event_store.add_event(input2._2(1)).isEmpty)
    assert(event_store.add_event(input2._2(2)).isDefined)
    val seq_match2 = event_store.get_most_recent_match()
    assert(seq_match2.size == 3)
    assert(seq_match2(0) == input2._1(0))
    assert(seq_match2(1) == input2._1(1))
    assert(seq_match2(2) == input2._1(2))

  }

  test("Testing JSON parser") {
    val input =
      """
        |{
        | "event_types": [
        |   {"name": "A", "fields": ["id", "field1", "field2"]},
        |   {"name": "B", "fields": ["id", "field1", "field2"]},
        |   {"name": "C", "fields": ["id", "field1", "field2"]},
        |   {"name": "D", "fields": ["id", "field1", "field2"]}],
        | "query":
        |   {"event_clause":
        |     {"event_seq": [
        |       {"event_type": "A", "name": "a", "negated": false, "order": 0},
      |         {"event_type": "C", "name": "c", "negated": false, "order": 1},
      |         {"event_type": "D", "name": "d", "negated": false, "order": 2}
    |         ]},
|         "where":
|           {"expr_root":
|               {
|                 "op": "and",
|                 "left": "true",
|                 "right": "true"
|                }
|              },
|          "within": {
|           "magnitude": 1,
|           "unit": "MINUTES"
|           }
|         }
|       }""".stripMargin

    val res = cep.core.query.fromJson(input)
    assert(res.isRight)
    assert(res.right.get.query.where.isDefined)
    assert(res.right.get.query.within.isDefined)
  }

  test("Testing JSON parser optional sections") {
    val input =
      """
        |{
        | "event_types": [
        |   {"name": "A", "fields": ["id", "field1", "field2"]},
        |   {"name": "B", "fields": ["id", "field1", "field2"]},
        |   {"name": "C", "fields": ["id", "field1", "field2"]},
        |   {"name": "D", "fields": ["id", "field1", "field2"]}],
        | "query":
        |   {"event_clause":
        |     {"event_seq": [
        |       {"event_type": "A", "name": "a", "negated": false, "order": 0},
        |         {"event_type": "C", "name": "c", "negated": false, "order": 1},
        |         {"event_type": "D", "name": "d", "negated": false, "order": 2}
        |         ]},
        |         "where":
        |           {"expr_root":
        |               {
        |                 "op": "none",
        |                 "left": "true",
        |                 "right": "true"
        |                }
        |              },
        |          "within": {
        |           "magnitude": 0,
        |           "unit": "MINUTES"
        |           }
        |         }
        |       }""".stripMargin

    val res = cep.core.query.fromJson(input)
    assert(res.isRight)
    assert(res.right.get.query.where.isEmpty)
    assert(res.right.get.query.within.isEmpty)
  }

}