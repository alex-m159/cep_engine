module Domain.Controllers.Rest.RestQueryController

open System.Text.Json
open Domain.Core.Query
open Domain.Ports.QueryService
open Domain.Adaptors.FSAExecutor
open Suave
open FSharp.Json

type Nexter<'T>(a: 'T[]) =
    
    let mutable current_index = 0
    
    member this.next(): Option<'T> =
        let ret = 
            if current_index < a.Length then
                Some a[current_index]
            else
                None
        current_index <- current_index + 1
        ret

type RestQueryController(service: QueryService) =

    member this.parse(req: HttpRequest) =
        let (unparsed, _) = req.form[0]
        unparsed
            |> service.parseJson
            |> Json.serialize

    member this.testThread(req: HttpRequest) =
        let (unparsed, _) = req.form[0]
        let query = unparsed |> service.parseJson
            
        let states = toDFA(query)


        let fields =  [StringField("field1"); IntField("field2")] 
        let etA = {
            name = "A";
            fields = fields;
        }
        let epA = {
            event_type = etA;
            param_name = "a";
            order = 0;
        }
        let etC = {
            name = "C";
            fields = fields;
        }
        let epC = {
            event_type = etC;
            param_name = "c";
            order = 1;
        }
        let etD = {
            name = "D";
            fields = fields;
        }
        let epD = {
            event_type = etD;
            param_name = "d";
            order = 2;
        }
        let bound_fields =  [BoundStringField("field1", "text"); BoundIntFIeld("field2", 1)]
        let a = {
            param = epA;
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

        let n = new Nexter<EventBinding>([|a; c; d|])        
        
        let fsa = new FSMSSC(states, (fun () -> n.next()), (fun (b) -> printfn $"Received from FSMSSC: {b}") ) 
        fsa.launchThread()
        "All good"