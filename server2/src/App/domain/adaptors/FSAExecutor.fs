module Domain.Adaptors.FSAExecutor

open Domain.Core.Operators
open Domain.Core.Query
open Domain.Ports.PlanExecutor
open FSharp
open FSharp.Collections

open System.Threading.Channels
open System.Threading

(*
    Options:
    * Fresh implementation in server model
    * Fresh implementation in in-process model (ala SQLite)
    * Wrapper/UI around Flink implementation
    * Wrapper/UI around other DB
*)

type State = 
    {
        event_param: Option<EventParam>
        event_type: EventType
        success_state: bool
        failure_state: bool
        transitions: Map<string, State>
    }

let StartingType = {
    name = "starting";
    fields = []
}

type StateSummary = 
    NOT_STARTED | ACCEPTING_EVENTS | SUCCESS | FAILURE

let startState first =
    {
        event_param = None;
        event_type = StartingType;
        success_state = false;
        failure_state = false;
        transitions = Map( seq { (first.event_type.name, first) } )
    }

let makeSuccessState et =
    let s: State = {
        event_param = None
        event_type = et
        success_state = true;
        failure_state = false;
        transitions = Map.empty;
    }
    (s.event_type.name, s)

// let makeState(ep): State =
//     let s = 
//         {
//             event_param = ep;
//             event_type = ep.event_type;
//             starting = if ep.order = 0 then true else false;
//             ending = false;
//             failed = false;
//             success = [];
//             failure = [];
//         }

// let rec subToState(lst: List<EventParam>): List<State> =

//     match lst with
//         | head :: [] -> 
//             let s = {
//                 success_state = true;
//                 failure_state = false;
//                 transitions = Map.empty
//             }
//             [s]
//         | head :: tail -> 
//             let states = subToState(tail)
//             let next = List.head states
//             let s = {
//                 success_state = false;
//                 failure_state = false;
//                 transitions = Map.empty
//             }
//             s :: states

let onlyEventParam(sub: SubSeqExpr): EventParam =
    match sub with
        | EventParam(ep) -> ep
        | Not(ep) -> ep

let toDFA(query: Query): State = 
    // let EventClause(seq_expr: SeqExpr) = query.event_clause 

    let states: State = match query.event_clause with
        | Event(SingletonSeq(EventParam(param))) -> 
            let s = {
                event_param = Some param;
                event_type = param.event_type;
                success_state = true;
                failure_state = false;
                transitions = Map.empty;
            }
            let start = {
                event_param = Some param;
                event_type = param.event_type;
                success_state = false;
                failure_state = false;
                transitions = Map(seq { (s.event_type.name, s) });
            }
            start
        | Event(SingletonSeq(Not(param))) -> 
            let success_states = query.event_types |> Seq.map(makeSuccessState)
            let start = {
                event_param = Some param;
                event_type = param.event_type;
                success_state = false;
                failure_state = false;
                transitions = Map(success_states)
            }
            start
            
        | Event(Seq(subs)) ->

            match subs |> Array.ofSeq with

                | [| f; s  |] ->
                    let second = match s with 
                        | EventParam(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = true;
                                failure_state = false;
                                transitions = Map.empty;
                            }
                        | Not(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = true;
                                failure_state = false;
                                transitions = Map.empty;
                            }
                    let first = match f with 
                        | EventParam(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map(seq {(second.event_type.name, second)})
                            }
                        | Not(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map.empty
                            }
                    
                    startState(first)
                | [| f; s;  t|] ->
                    printfn "Running 3 event block"
                    let third = match t with 
                        | EventParam(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = true;
                                failure_state = false;
                                transitions = Map.empty
                            }
                        | Not(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = true;
                                failure_state = false;
                                transitions = Map.empty
                            }

                    let second = match s with 
                        | EventParam(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map(seq { (third.event_type.name, third) })
                            }
                        | Not(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map(seq { (third.event_type.name, third) });
                            }
                    let first = match f with 
                        | EventParam(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map(seq { (second.event_type.name, second) });
                            }
                        | Not(p) ->
                            {
                                event_param = Some p;
                                event_type = p.event_type;    
                                success_state = false;
                                failure_state = false;
                                transitions = Map(seq { (second.event_type.name, second) });
                            }
                    
                    startState(first)
        | _ -> 
            {
                event_param = None;
                event_type = StartingType;
                success_state = false;
                failure_state = false;
                transitions = Map.empty
            }
    
    states

let nextState(event: EventBinding, current: State * StateSummary * EventBinding[], all: State): State * StateSummary * EventBinding[] =

    let (state, summary, prev_events) = current

    match state.transitions.TryFind(event.param.event_type.name) with
        | None -> current
        | Some next -> 
                let e = [|event|]
                let appended: EventBinding[] = Array.append prev_events e 
                let new_summary = 
                    match summary with
                        | NOT_STARTED -> ACCEPTING_EVENTS
                        | ACCEPTING_EVENTS when next.success_state -> SUCCESS
                        | ACCEPTING_EVENTS when next.failure_state -> FAILURE
                        | s -> s
                (next, new_summary, appended)


type FSMSSC(state: State, input: unit -> Option<EventBinding>, output: Option<EventBinding> -> unit) =
    // let input = Channel.CreateUnbounded<EventBinding>()
    // let output = Channel.CreateUnbounded<bool>()
    let th = new Thread(fun () -> FSMSSC.runstuff(state, input, output))
    member this.launchThread() =
        th.Start()
        ()

    static member runCpu() =
        let mutable num = 1
        for i in [1..100000] do
            num <- ((num + i) / num )
    
    static member processEvent(event: EventBinding, current: State * StateSummary * EventBinding[], state: State): State * StateSummary * EventBinding[] =
        nextState(event, current, state)

    static member runstuff(state: State, input: unit -> Option<EventBinding>, output: Option<EventBinding> -> unit): unit = 
        printfn "Starting runstuff()"
        let mutable current: State * StateSummary * EventBinding[] = (state, NOT_STARTED, [||])
        let mutable loop = true

        while loop do
            let event = input()
            match event with 
                | None ->
                    loop <- false
                | Some e ->
                    let next = FSMSSC.processEvent(e, current, state)
                    current <- next
        ()
    



