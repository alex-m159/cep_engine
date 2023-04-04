module Domain.Ports.QueryService

open Domain.Core.Query

type QueryService =
    abstract member parseJson: string -> Query
    // abstract member createQuery: CreateQueryCommand -> bool
    // abstract member stopQuery: int -> bool
    // abstract member queryStatus: int -> QueryStatus
    // abstract member allQueries: () -> Query[]
    // abstract member getById: int -> Option<Query>
    // abstract member deleteQuery: int -> Option<Query>
