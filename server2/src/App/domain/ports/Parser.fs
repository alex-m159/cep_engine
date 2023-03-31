namespace Domain.Ports.Parser

open Domain.Core.Query

type QueryParser =
    abstract member parse : string -> Query