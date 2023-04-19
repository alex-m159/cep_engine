module Tests

open System
open Xunit

open Domain.Core.Query
open Domain.Adaptors.FSAExecutor
open Domain.Controllers.Rest.RestQueryController
// open FSharp.Json
open System.Text.Json
open System.Text.Json.Serialization

[<Fact>]
let ``My test`` () =
    Assert.True(true)

[<Fact>]
let ``Fail every time`` () = 
    // Assert.True(false)
    Assert.True(true)





let getSummary(op: State * StateSummary * EventBinding[]): string =
    let (_, s, _) = op
    match s with
        | NOT_STARTED -> "NOT_STARTED"
        | ACCEPTING_EVENTS -> "ACCEPTING_EVENTS"
        | SUCCESS -> "SUCCESS"
        | FAILURE -> "FAILURE"

let getState(op: State * StateSummary * EventBinding[]): string =
    let (s, _, _) = op
    s |> fun (s) -> $"{(s.event_type.name)}"


let getEvents(op: State * StateSummary * EventBinding[]): string =
    let (_, _, e) = op
    if e.Length > 0 then
        let s = e |> Array.map(fun (ev) -> ev.param.param_name) |> fun(a) -> String.Join(",", a)
        $"[{s}]"
    else 
        "[||]"

let getString(op: State * StateSummary * EventBinding[]): string =
    $"{getState(op)} - {getSummary(op)} - {getEvents(op)}"

[<Fact>]
let ``Test Finite State SSC`` () =

    (* Fields *)
    let fields = [StringField("field1"); IntField("field2")]

    (* Event Type Definitions *)
    let etA = {
        name = "A";
        fields = fields;
    }

    let etB = {
        name = "B";
        fields = fields;
    }

    let etC = {
        name = "C";
        fields = fields;
    }


    (* Event Parameters *)
    let epA = {
        event_type = etA;
        param_name = "a";
        order = 0;
    }
    
    let epB = {
        event_type = etB;
        param_name = "b";
        order = 1;
    }
    
    let epC = {
        event_type = etC;
        param_name = "c";
        order = 2;
    }

    (* Event Clause and Seq *)

    let query_seq = Seq( [EventParam(epA); EventParam(epB); EventParam(epC);] )
    let event = Event query_seq

    let query: Query = {
        event_types = [ etA; etB; etC;];
        event_clause = event;
        where = None
        within = None
    }

    let state = toDFA(query)

    // 1. Either create the serializer options from the F# options...
    let options =
        JsonFSharpOptions.Default()
            // Add any .WithXXX() calls here to customize the format
            .ToJsonSerializerOptions()

    // 2. ... Or add the F# options to existing serializer options.
    JsonFSharpOptions.Default()
        // Add any .WithXXX() calls here to customize the format
        .AddToJsonSerializerOptions(options)

    // 3. Either way, pass the options to Serialize/Deserialize.
    // JsonSerializer.Serialize({| x = "Hello"; y = "world!" |}, options)
    // --> {"x":"Hello","y":"world!"}

    // printfn "Generated DFA"
    // let json = JsonSerializer.Serialize(states, options)
    
    

    (* Event and Field Bindings  - Runtime Events *)
    let bound_fields = [BoundStringField("field1", "text"); BoundIntFIeld("field2", 1)]
    let a = {
        param = epA;
        bound_fields = bound_fields;
    }

    let b = {
        param = epB;
        bound_fields = bound_fields;
    }

    let c = {
        param = epC;
        bound_fields = bound_fields;
    }

    let mutable current: State * StateSummary * EventBinding[] = (state, NOT_STARTED, [||])
    let events = [|a; b; c;|] 
    for e in events do
        current <- FSMSSC.processEvent(e, current, state)
        
    let (state, summary, events) = current
    let res = summary = SUCCESS
    Assert.True(res)

    // Assert.True(true)

[<Fact>]
let ``Test Optional in Query`` () =

    (* Fields *)
    let fields = [StringField("field1"); IntField("field2")]

    (* Event Type Definitions *)
    let etA = {
        name = "A";
        fields = fields;
    }

    let etB = {
        name = "B";
        fields = fields;
    }

    let etC = {
        name = "C";
        fields = fields;
    }

    let etD = {
        name = "D";
        fields = fields;
    }


    (* Event Parameters *)
    let epA = {
        event_type = etA;
        param_name = "a";
        order = 0;
    }
    
    let epB = {
        event_type = etB;
        param_name = "b";
        order = 1;
    }
    
    let epC = {
        event_type = etC;
        param_name = "c";
        order = 2;
    }

    let epD = {
        event_type = etD;
        param_name = "d";
        order = 3;
    }

    (* Event Clause and Seq *)

    let query_seq_list = [EventParam(epA); Optional(epB); Optional(epC); EventParam(epD)]
    let query_seq = Seq( query_seq_list )
    let event = Event query_seq

    let query: Query = {
        event_types = [ etA; etB; etC; etD;];
        event_clause = event;
        where = None
        within = None
    }

    let state = toDFA(query)

    // 1. Either create the serializer options from the F# options...
    let options =
        JsonFSharpOptions.Default()
            // Add any .WithXXX() calls here to customize the format
            .ToJsonSerializerOptions()

    // 2. ... Or add the F# options to existing serializer options.
    JsonFSharpOptions.Default()
        // Add any .WithXXX() calls here to customize the format
        .AddToJsonSerializerOptions(options)

    // 3. Either way, pass the options to Serialize/Deserialize.
    // JsonSerializer.Serialize({| x = "Hello"; y = "world!" |}, options)
    // --> {"x":"Hello","y":"world!"}

    // printfn "Generated DFA"
    let json = JsonSerializer.Serialize(state, options)
    printfn $"makeFSM:"
    // printfn $"{json}"
    

    (* Event and Field Bindings  - Runtime Events *)
    let bound_fields = [BoundStringField("field1", "text"); BoundIntFIeld("field2", 1)]
    let a = {
        param = epA;
        bound_fields = bound_fields;
    }

    let b = {
        param = epB;
        bound_fields = bound_fields;
    }

    let c = {
        param = epC;
        bound_fields = bound_fields;
    }

    let d = {
        param = epD;
        bound_fields = bound_fields;
    }

    let mutable current: State * StateSummary * EventBinding[] = (state, NOT_STARTED, [||])
    let events = [|a; b; c; d;|] 
    for e in events do
        let (next, someevents) = FSMSSC.someResult(e, current, state)
        current <- next
                
        
    let (state, summary, events) = current
    let s = events |> Array.map(fun (ev) -> $"{ev.param.event_type.name} {ev.param.param_name}") |> fun(a) -> String.Join(",", a)
    printfn $"{summary} - [{s}]"
    let res = summary = SUCCESS
    Assert.True(res)
