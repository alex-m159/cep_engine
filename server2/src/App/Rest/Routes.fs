module Rest.Routes

open Suave
open Suave.Filters
open Suave.Operators
open Suave.Successful
open Suave.RequestErrors
open System.Collections.Generic


open Domain.Adaptors.JsonParser
open Domain.Controllers.Rest.RestQueryController


let createApp(query: RestQueryController) =
  choose [
    GET >=> path "/query" >=> OK "Hello query GET"
    GET >=> path "/query/show" >=> OK "Hello show GET"
    POST >=> path "/query/parse" >=> request (fun req -> OK (query.parse(req)))
    POST >=> path "/query/demo" >=> request (fun req -> OK (query.testThread(req)))
    NOT_FOUND "No route found"
  ]

