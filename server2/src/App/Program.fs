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

let run = 
    let json = """
        {
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
            }
        }
    """
    let parsed = JObject.Parse(json)
    parsed


[<EntryPoint>]
let main args =
    // printfn "Nice command-line arguments! Here's what System.Text.Json has to say about them:"

    // let value, json = getJson {| args=args; year=System.DateTime.Now.Year |}
    // printfn $"Input: %0A{value}"
    // printfn $"Output: %s{json}"

    // let cts = new CancellationTokenSource()
    // let conf = { defaultConfig with cancellationToken = cts.Token }
    // let listening, server = startWebServerAsync conf app
    
    // Async.Start(server, cts.Token)
    printfn "Make requests now"
    
        
    // cts.Cancel()
    let root = run.SelectToken("where.expr_root")
    let op = root.SelectToken("op").Value<string>()
    

    Console.ReadKey true |> ignore
    0 // return an integer exit code