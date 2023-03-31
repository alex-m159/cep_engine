namespace Domain.Core.Operators

// open Domain.Core.Query


type Operator<'a> =
    abstract member push : 'a -> unit

type SSC<'a> = 
    abstract member push : 'a -> unit

type Selection<'a> =
    abstract member push : 'a -> unit

type Window<'a> =
    abstract member push : 'a -> unit

type Negation<'a> =
    abstract member push : 'a -> unit

type Transformation<'a> =
    abstract member push : 'a -> unit

type Plan<'a> = Root of Operator<'a>

// type Planner =
//     abstract member plan : Query -> Plan