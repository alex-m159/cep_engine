module Domain.Ports.Planner

open Domain.Core.Query
open Domain.Core.Operators

type Planner<'T> =
    abstract member logical_plan: Query -> Plan<'T>
    abstract member physical_plan: Plan<'T> -> Plan<'T>