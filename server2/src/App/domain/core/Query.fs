namespace Domain.Core.Query

(*
    Language Constructs 
*)
type Field = 
    StringField of string
    | IntField of string 


type EventType = 
    {
        name: string;
        fields: List<Field>
    }

type EventParam = 
    {
        event_type: EventType;
        param_name: string;
        order: int;
    }

// For run-time values
type FieldBinding =
    BoundStringField of string * string
    | BoundIntFIeld of string * int

type EventBinding =
    {
        param: EventParam
        bound_fields: FieldBinding
    }

type ComparisonOp = GT | GEq | LT | LEq | Eq | NEq

type EventField =
    {
        event_param: EventParam
        field_name: string
    }

type PredicateConstant =
    StringConst of string
    | IntConst of int

type SimplePredicate = 
    {
        op: ComparisonOp
        left: EventField
        right: PredicateConstant
    }

type ParamPredicate = 
    {
        op: ComparisonOp
        left: EventField
        right: EventField
    }

type Predicate = SimplePredicate of SimplePredicate | ParamPredicate of ParamPredicate

type BooleanOps = And | Or

type WhereExpr = 
    BaseExpr of Predicate
    | CompoundExpr of WhereExpr * BooleanOps * WhereExpr

type Where = 
    {
        root: WhereExpr
    }

(*
    p
    NOT(p)
    SEQ(p, p, p)
    SEQ(p, NOT(p), p)

    NOT
        SEQ(p, SEQ(p, p), p)
*)


type SubSeqExpr = 
    EventParam of EventParam
    | Not of EventParam

type SeqExpr = 
    SingletonSeq of SubSeqExpr
    | Seq of List<SubSeqExpr>
    


type EventClause = Event of SeqExpr

type TimeUnits = MINUTE | HOUR | SECOND 

type Within = 
    {
        units: TimeUnits
        magnitude: int
    }

type Query = 
    {
        event_types: EventType[]
        event_clause: EventClause
        where: Option<Where>
        within: Option<Within>
    }


type QueryParser =
    abstract member parse : string -> Query

(*
let somename: Query = 
    let a = {
        name = "A";
        fields = [IntField("field1", 100), IntField("field2", 101), IntField("field3", 102)]
    }
    let b = {
        name = "B";
        fields = [IntField("field1", 200), IntField("field2", 201), IntField("field3", 202)]
    }
    let c = {
        name = "C";
        fields = [IntField("field1", 300), IntField("field2", 301), IntField("field3", 302)]
    }

    let q = {
        event_types = [a, b, c];
        event_clause =  
    }
*)  


// TODO: Parse JSON in query types
