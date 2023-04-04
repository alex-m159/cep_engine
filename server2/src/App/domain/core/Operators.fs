namespace Domain.Core.Operators

open FSharp.Collections
open Domain.Core.Query
(*
    Still debating between potential designs.
    One is that every operator is it's own object and has its downstream operators as arguments.
    Another is that every operator is just a data type and then there are different execution functions
    that will run different code based on each physical operator
*)

type DataSource<'T> = DataSource of seq<'T>

(* Logical Operators *)
type Operator<'T> =
    SSC of DataSource<'T>
    | Selection of WhereExpr * Operator<'T>
    | Window of Within * Operator<'T>
    | Negation of SubSeqExpr * Operator<'T>
    | Transformation of Operator<'T>

type Plan<'T> = Plan of Operator<'T>
