module Domain.Adaptors.FSAExecutor

open Domain.Core.Operators
open Domain.Core.Query
open Domain.Ports.PlanExecutor
open FSharp
open FSharp.Collections

open System.Threading.Channels
open System.Threading



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


let onlyEventParam(sub: SubSeqExpr): EventParam =
    match sub with
        | EventParam(ep) -> ep
        | Not(ep) -> ep

// The transition seq is passed back just so that the states can be added to the optional carry
let buildNextState(p: EventParam, next: State, neg_states, op_carry): State = 
    let neg_seq = seq {for state in neg_states do yield (state.event_type.name, state)}
    let immediate_next = seq { (next.event_type.name, next) }
    let carry_seq = seq { for i in op_carry do yield (i.event_type.name, i) }
    let all = Seq.append (Seq.append immediate_next neg_seq) carry_seq
    let current = {
        event_param = Some p;
        event_type = p.event_type;
        success_state = false;
        failure_state = false;
        transitions = Map( all );
    }
    current
 
(*
    Pre-conditions:
        The first and last SubSeqExpr is not Optional or Not events.
        Argument is list of at least 2 events
        Not events cause the SSC to move to failure state
        since this function assumes that there are no WHERE conditions and no WITHIN conditions
    
    Implementation Notes:
        This function will process the last element of the SEQ first and 
        as it goes backwards towards the first element, it will pass along Not() states (neg_states)
        and Optional() carries (op_carry).

        The Not() states are added as transitions to the next EventParam() or Optional(),
        and the Optional() carries are added as transition to the next EventParam() param.

        This is necessary since Not() parameters are dead-ends for the State graph, and we can't just 
        copy over their transitions to skip over them. The Optional() carries are necessary because if there are a
        series of Optional() (or Not()) elements, we'll want all their downstream transitions to be
        carried back to the next earliest EventParam() so that Optional() and Not() States can 
        be skipped over when needed. You can't do either of these by simply operating on elements 
        pair wise (i.e. (head :: second :: tail) ), so we need to pass back the State transitions 
        until we find an EventParam() to attach them to, and then we can clear the carries list. 
*)
let rec makeFSM(s: List<SubSeqExpr>): (State * List<State> * List<State>) =
    match s with 
        | head :: [] ->
            let current = match head with
                | EventParam(p) -> 
                    {
                        event_param = Some p;
                        event_type = p.event_type;
                        success_state = true;
                        failure_state = false;
                        transitions = Map.empty;
                    }
                | Not(p) | Optional(p) ->
                    raise (System.ArgumentException($"Event parameter {head} must be EventParam() since this FSA construction function assumes that the query has no where or within clause"))
            ( current, [], [])

        | head :: second :: [] ->
            let (next, neg_states, op_carry) = makeFSM( second::[] )
            
            // This shouldn't happen if the precondition is followed, but
            // if second is not or optional, then head should be a success state
            // because it may be the final event before sequence completion.
            match second with 
                | Not(p) | Optional(p) -> 
                    raise (System.ArgumentException($"Event parameter {second} must be EventParam() since this FSA construction function assumes that the query has no where or within clause"))
                | _ -> ()

            match head with 
                | EventParam(p) ->
                    let current = buildNextState(p, next, neg_states, op_carry)
                    // We can clear the Not() and Optional() carries since EventParams() are not 
                    // skipped over in the State graph
                    (current,  [], []) 

                | Optional(p) ->
                    let current = buildNextState(p, next, neg_states, op_carry)
                    let next_carry = List.ofSeq( seq { for i in current.transitions.Values do yield i } )
                    // We clear the Not() carries since they're now included in the Optional() carries
                    // and will be attached to the next EventParam()
                    (current,  [], next_carry)

                | Not(p) -> 
                    let current = {
                        event_param = Some p;
                        event_type = p.event_type;
                        success_state = false;
                        failure_state = true;
                        transitions = Map( Seq.empty );
                    }
                    (next,  current :: neg_states, op_carry) 
        | head :: second :: tail ->
            let (next, neg_states, op_carry) = makeFSM( second::tail )                   
            
            match head with
                | EventParam(p) ->
                    let current = buildNextState(p, next, neg_states, op_carry)
                    (current,  [], [])
                | Optional(p) -> 
                    let current = buildNextState(p, next, neg_states, op_carry)
                    let next_carry = List.ofSeq( seq { for i in current.transitions.Values do yield i } )
                    (current,  [], next_carry)

                | Not(p) -> 
                    let current = {
                        event_param = Some p;
                        event_type = p.event_type;
                        success_state = false;
                        failure_state = true;
                        transitions = Map( Seq.empty );
                    }
                    (next,  current :: neg_states, op_carry) 
                

let toDFA(query: Query): State = 
    match query.event_clause with
        | Event(SingletonSeq(EventParam(param))) -> 
            let s = {
                event_param = Some param;
                event_type = param.event_type;
                success_state = true;
                failure_state = false;
                transitions = Map.empty;
            }
            let start = {
                event_param = None;
                event_type = StartingType;
                success_state = false;
                failure_state = false;
                transitions = Map(seq { (s.event_type.name, s) });
            }
            start
        | Event(SingletonSeq(Not(param))) -> 
            let success_states = query.event_types |> Seq.map(makeSuccessState)
            let start = {
                event_param = None;
                event_type = StartingType;
                success_state = false;
                failure_state = false;
                transitions = Map(success_states)
            }
            start
            
        | Event(Seq(subs)) ->
            let (state, _, _) = makeFSM(List.ofSeq subs)
            startState state

        | _ -> 
            {
                event_param = None;
                event_type = StartingType;
                success_state = false;
                failure_state = false;
                transitions = Map.empty
            }
    

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
    
    static member someResult(
        event: EventBinding, 
        current: State * StateSummary * EventBinding[], 
        state: State): (State * StateSummary * EventBinding[]) * Option<EventBinding[]> =
        let res = FSMSSC.processEvent(event, current, state)
        match res with
            | (s, SUCCESS, es) -> ((s, SUCCESS, es), Some es )
            | x -> (x, None )


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
    



