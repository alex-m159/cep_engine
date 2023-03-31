namespace Domain.Adaptors

open FSharp.Data
open Domain.Core.Query

(* 
    TODO: In the event_seq change "negated" from being a 
        property of a single event parameter to being an operator under which an event is placed
*)
module Query = 
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
                    "op": "s_eq",
                    "left_var": "a",
                    "left_field": "field1",
                    "literal": "100"
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

    let jsonNodeToEventType(event_type: QueryJson.EventType) = 
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

    let extractName event_type =
        (event_type.name, event_type)

    let json = QueryJson.Parse(sample)
    let run = 

        
        let arr = ResizeArray()
        for event_type in json.EventTypes do
            printfn $"{event_type.Name}"
            for field in event_type.Fields do
                printfn $"{field}"
                arr.Add(field)

        let f: Field = IntField "field1"
        arr

    type JsonParser =
        member this.parse(jsonstring: string): Query = (this :> QueryParser).parse(jsonstring)
        
        interface Domain.Core.Query.QueryParser with
            member this.parse jsonstring =

                let parsed = QueryJson.Parse(sample)

                (* Event Type Definitions *)
                // let all_event_types = ResizeArray<EventType>()
                // let mutable type_map = Map.empty
                let event_types: EventType[] = parsed.EventTypes |> Array.map(jsonNodeToEventType)
                let type_map = event_types |> Array.map(extractName) |> Map.ofArray

                (* Event Clause *)
                let _sub_seqs = ResizeArray<SubSeqExpr>()                
                for param in parsed.Query.EventClause.EventSeq do
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
                


                let q: Query = {
                    event_types = event_types; 
                    event_clause = event_clause; 
                    where = None; 
                    within = None; 
                }
                q
    

