module Domain.Ports.PlanExecutor

open Domain.Core.Operators
// MultiThreadedExecutor
// MultiProcessExecutor
// SingleThreadedExecutor
// ContinuousExecutor
// MicrobatchExecutor
// PullbasedExecutor
// PushbasedExecutor

(*
    [] Watermark
    [] Early/On-Time/Late Handling
    [] Latency controls
    [] Back-pressure
    [] Result updating
    [] Exactly-Once and Side Effects
    
*)

type PlanExecutor<'T> =
    abstract member run : Plan<'T> -> unit
