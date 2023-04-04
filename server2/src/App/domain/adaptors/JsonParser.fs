module Domain.Adaptors.JsonParser

open FSharp.Data
open Domain.Core.Query
open Domain.Ports.Parser
open Newtonsoft.Json.Linq

(* 
    TODO: In the event_seq change "negated" from being a 
        property of a single event parameter to being an operator under which an event is placed
*)
[<Literal>]
let sample = """
{
    "event_types": [
        {
            "name": "A",
            "fields": [
                {
                    "name": "id",
                    "type": "integer"
                },
                {
                    "name": "field1",
                    "type": "string"
                },
                {
                    "name": "field2",
                    "type": "string"
                }
            ]
        },
        {
            "name": "B",
            "fields": [
                {
                    "name": "id",
                    "type": "integer"
                },
                {
                    "name": "field1",
                    "type": "string"
                },
                {
                    "name": "field2",
                    "type": "string"
                }
            ]
        },
        {
            "name": "C",
            "fields": [
                {
                    "name": "id",
                    "type": "integer"
                },
                {
                    "name": "field1",
                    "type": "string"
                },
                {
                    "name": "field2",
                    "type": "string"
                }
            ]
        },
        {
            "name": "D",
            "fields": [
                {
                    "name": "id",
                    "type": "integer"
                },
                {
                    "name": "field1",
                    "type": "string"
                },
                {
                    "name": "field2",
                    "type": "string"
                }
            ]
        }
    ],
    "query": {
        "event_clause": {
            "event_seq": [
                {
                    "event_type": "A",
                    "name": "a",
                    "negated": false,
                    "order": 0
                },
                {
                    "event_type": "C",
                    "name": "c",
                    "negated": true,
                    "order": 1
                },
                {
                    "event_type": "D",
                    "name": "d",
                    "negated": false,
                    "order": 2
                }
            ]
        },
        "where": {
            "expr_root": {
                "op": "and",
                "left": {
                    "op": "s_eq",
                    "left_var": "a",
                    "left_field": "field1",
                    "literal": "100"
                },
                "right": {
                    "op": "p_eq",
                    "left_var": "d",
                    "left_field": "field1",
                    "right_var": "a",
                    "right_field": "field1"
                }
            }
        },
        "within": {
            "magnitude": 5,
            "unit": "HOURS"
        }
    }
}
"""

type QueryJson = JsonProvider<sample>
    


type JsonParser() =
    member this.parse(jsonstring: string): Query = (this :> QueryParser).parse(jsonstring)
    
    static member private parseEventTypes(event_type: QueryJson.EventType) =
        let event_name = event_type.Name 
        let fields_for_event = ResizeArray<Field>()
        for field in event_type.Fields do
            let field_name = field.Name
            let field_type = field.Type
            
            let field: Field = 
                match field_type with
                    | "string" -> StringField(field_name)
                    | "integer" -> IntField(field_name)
                    | ft -> raise( System.ArgumentException($"Field Type must be string or integer, but was {ft}"))
            fields_for_event.Add(field)
        let et: EventType = {name = event_name; fields = List.ofSeq(fields_for_event);}
        et
    
    static member private extractKV event_type =
        (event_type.name, event_type)

    static member private parseEventClause(type_map: Map<string, EventType>, event_clause: QueryJson.EventClause) = 
        let _sub_seqs = ResizeArray<SubSeqExpr>()                
        for param in event_clause.EventSeq do
            let param_type_name = param.EventType
            let param_name = param.Name
            let param_negated = param.Negated
            let param_order = param.Order
            let event_param: EventParam = 
                {
                    event_type = type_map.[param_type_name]
                    param_name = param_name;
                    order = param_order;
                }
            let seq_expr = 
                if param_negated then
                    Not event_param
                else
                    EventParam event_param
            _sub_seqs.Add(seq_expr)
        let sub_seqs = List.ofSeq(_sub_seqs)
        
        let event_clause = 
            if List.length(sub_seqs) = 1 then
                Event( SingletonSeq(List.head(sub_seqs)) )
            else
                Event( Seq(sub_seqs) )
        event_clause

    static member private fieldNameFilter name field =
        let result: bool = match field with
            | StringField fieldname -> fieldname = name
            | IntField fieldname -> fieldname = name
        result

    static member private stringToOp(op: string): ComparisonOp =
        match op with
            | "s_eq" | "p_eq" -> Eq
            | "s_ne" | "p_ne" -> NEq
            | "s_gt" | "p_gt" -> GT
            | "s_gte" | "p_gte" -> GTEq
            | "s_lt" | "p_lt" -> LT
            | "s_lte" | "p_lte" -> LTEq
            | _ -> raise (System.ArgumentException($"Operation ${op} is not a valid operation"))

    static member private recurseWhere(param_map: Map<string, EventParam>, expr: JToken): WhereExpr =
        match expr.SelectToken("op").Value<string>() with
            | op when op.StartsWith("s_") -> 
                let param: EventParam = param_map.[expr.SelectToken("left_var").Value<string>()]
                let left_field: string = expr.SelectToken("left_field").Value<string>()
                let literal: string = expr.SelectToken("literal").Value<string>()
                let fields_arr = param.event_type.fields
                let find_pred = JsonParser.fieldNameFilter left_field
                let pred = match fields_arr |> Seq.find(find_pred) with 
                    | StringField(_) ->  
                        let pred: SimplePredicate = {
                            op = JsonParser.stringToOp(op); 
                            left = {event_param = param; field_name = left_field;}; 
                            right = StringConst(literal)
                        }
                        pred
                    | IntField(_) ->
                        let pred: SimplePredicate = {
                            op = JsonParser.stringToOp(op); 
                            left = {event_param = param; field_name = left_field;}; 
                            right = IntConst(int(literal))
                        }
                        pred
                BaseExpr(SimplePred(pred))
                        
            | op when op.StartsWith("p_")  -> 
                let left_param: EventParam = param_map.[expr.SelectToken("left_var").Value<string>()]
                let left_field: string = expr.SelectToken("left_field").Value<string>()
                let right_param: EventParam = param_map.[expr.SelectToken("right_var").Value<string>()]
                let right_field: string = expr.SelectToken("right_field").Value<string>()
                let pred = {
                    op = JsonParser.stringToOp(op); 
                    left = {event_param = left_param; field_name = left_field;}; 
                    right = {event_param = right_param; field_name = right_field;}; 
                }
                BaseExpr(ParamPred(pred))
            | "and" -> CompoundExpr(JsonParser.recurseWhere(param_map, expr.SelectToken("left")), And, JsonParser.recurseWhere(param_map, expr.SelectToken("right")) )
            | "or" -> CompoundExpr(JsonParser.recurseWhere(param_map, expr.SelectToken("left")), Or, JsonParser.recurseWhere(param_map, expr.SelectToken("right")) )

    static member private parseWhere(param_map: Map<string, EventParam>, root: JToken): Where =
        WhereRoot(JsonParser.recurseWhere(param_map, root))
    
    static member private singleton(seqexpr: SubSeqExpr): EventParam =
        match seqexpr with
            | EventParam(ep) -> ep
            | Not(ep) -> ep

    static member private extractEventParams( ec: EventClause): seq<EventParam> =
        match ec with
            | Event(Seq(list)) -> list |> Seq.map(JsonParser.singleton)
            | Event(SingletonSeq(single)) ->  
                let s = single |> JsonParser.singleton
                Seq.ofList([s])

    static member private extractKV(param: EventParam): string * EventParam =
        (param.param_name, param)

    static member private parseWithin(root: JToken): Within =
        let magnitude = root.SelectToken("magnitude").Value<int>()
        let str_unit = root.SelectToken("unit").Value<string>()
        let timeunit = match str_unit with
            | "MINUTES" | "MINUTE" -> MINUTE
            | "HOURS" | "HOUR" -> HOUR
            | "SECONDS" | "SECOND" -> SECOND                
            | _ -> raise( System.ArgumentException($"Time unit {str_unit} is not an accepted time unit for WITHIN clause"))
        Within(timeunit, magnitude)

    interface QueryParser with
        member this.parse jsonstring =

            let parsed = QueryJson.Parse(sample)


            (* Event Type Definitions *)
            let event_types: List<EventType> = parsed.EventTypes |> Seq.map(JsonParser.parseEventTypes) |> List.ofSeq
            let type_map = event_types |> Seq.map(JsonParser.extractKV) |> Map.ofSeq

            (* Event Clause *)
            let event_clause: EventClause = JsonParser.parseEventClause(type_map, parsed.Query.EventClause)

            (* Where Clause *)
            let j = JObject.Parse jsonstring
            let where_node = j.SelectToken "query.where.expr_root"
            
            let param_map: Map<string, EventParam> = 
                JsonParser.extractEventParams event_clause |>
                 Seq.map(JsonParser.extractKV) |> Map.ofSeq

            let where: Where = JsonParser.parseWhere(param_map, where_node)

            (* WITHIN Clause *)
            
            let within_node = j.SelectToken "query.within"
            let within: Within = JsonParser.parseWithin(within_node)

            let q: Query = {
                event_types = event_types; 
                event_clause = event_clause; 
                where = Some where; 
                within = Some within; 
            }
            q


