module Server

open System
// open Library
open Suave
open System.Threading
open Suave.Json
open System.Runtime.Serialization

open Rest.Routes
open Newtonsoft.Json
open Newtonsoft.Json.Linq

open Domain.Adaptors.JsonParser
open Domain.Adaptors.QueryServiceImpl
open Domain.Controllers.Rest.RestQueryController
open Rest.Routes

let json_parser: JsonParser = new JsonParser()
let service: QueryServiceImpl = new QueryServiceImpl(json_parser)
let query: RestQueryController = new RestQueryController(service)




[<EntryPoint>]
let main args =
    // printfn "Nice command-line arguments! Here's what System.Text.Json has to say about them:"

    // let value, json = getJson {| args=args; year=System.DateTime.Now.Year |}
    // printfn $"Input: %0A{value}"
    // printfn $"Output: %s{json}"
    
    let app = createApp(query)

    let cts = new CancellationTokenSource()
    let conf = { defaultConfig with cancellationToken = cts.Token }
    let listening, server = startWebServerAsync conf app
    
    Async.Start(server, cts.Token)
    printfn "Make requests now"
    
        
    // let root = run.SelectToken("where.expr_root")
    // let op = root.SelectToken("op").Value<string>()
    

    Console.ReadKey true |> ignore
    cts.Cancel()

    0 // return an integer exit code