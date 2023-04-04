module Domain.Adaptors.QueryServiceImpl

open Domain.Ports.QueryService
open Domain.Ports.Parser
open Domain.Core.Query


type QueryServiceImpl(parser: QueryParser) =
    interface QueryService with

        member this.parseJson(jsonstring: string): Query =
            parser.parse(jsonstring)
        // member this.createQuery: CreateQueryCommand -> bool
        // member this.stopQuery: int -> bool
        // member this.queryStatus: int -> QueryStatus
        // member this.allQueries: () -> Query[]
        // member this.getById: int -> Option<Query>
        // member this.deleteQuery: int -> Option<Query>
 

