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
        fields: seq<Field>
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
        bound_fields: seq<FieldBinding>
    }

type ComparisonOp = GT | GTEq | LT | LTEq | Eq | NEq

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

type Predicate = SimplePred of SimplePredicate | ParamPred of ParamPredicate

type BooleanOps = And | Or

type WhereExpr = 
    BaseExpr of Predicate
    | CompoundExpr of WhereExpr * BooleanOps * WhereExpr

type Where = WhereRoot of WhereExpr
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
    | Optional of EventParam
    // | Any of EventParam[]

type SeqExpr = 
    SingletonSeq of SubSeqExpr
    | Seq of seq<SubSeqExpr>
    


type EventClause = Event of SeqExpr

type TimeUnits = MINUTE | HOUR | SECOND 

type Within = Within of TimeUnits * int

type QueryStatus = RUNNING | STOPPED | FAILED | NOT_STARTED

type QueryInfo = 
    {
        query_id: int
        status: QueryStatus
    }

type Query = 
    {
        event_types: seq<EventType>
        event_clause: EventClause
        where: Option<Where>
        within: Option<Within>
    }

(* 
    End Language Constructs 
*)

type QueryResult = {
    events: seq<EventBinding>
}

type CreateQueryCommand = 
    {
        query: Query
    }


