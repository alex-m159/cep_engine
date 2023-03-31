module Server

open System
// open Library
open Suave
open System.Threading
open Suave.Json
open System.Runtime.Serialization

open Rest.Routes



[<DataContract>]
type TestInput =
    { [<field: DataMember(Name = "input")>]
        input : string
    }

[<DataContract>]
type TestOutput =
    { [<field: DataMember(Name = "output")>]
        output: string
    }

[<EntryPoint>]
let main args =
    // printfn "Nice command-line arguments! Here's what System.Text.Json has to say about them:"

    // let value, json = getJson {| args=args; year=System.DateTime.Now.Year |}
    // printfn $"Input: %0A{value}"
    // printfn $"Output: %s{json}"

    let cts = new CancellationTokenSource()
    let conf = { defaultConfig with cancellationToken = cts.Token }
    let listening, server = startWebServerAsync conf app
    
    Async.Start(server, cts.Token)
    printfn "Make requests now"
    Console.ReadKey true |> ignore
        
    cts.Cancel()

    0 // return an integer exit code