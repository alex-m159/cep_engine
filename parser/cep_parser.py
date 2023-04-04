from typing import List, Tuple, Dict
from lark import Lark
from lark.visitors import Interpreter
import json

l = Lark('''start: (event_def_list 
                    | query)+


            // EVENT DEFINITIONS //
            event_def_list: event_def+

            event_def: "TYPE"i name field_list
            field_list: "(" field ("," field)* ")"
            field: WORDP ":" type_name
            name:  WORDP
            type_name: "INTEGER"i -> integer | "STRING"i -> string


            // EVENT //
            query: event_clause [where] [within]
            event_clause: "EVENT"i seq

            
            seq: "SEQ"i "(" seq_param ("," seq_param)*  ")"
            ?seq_param: event_param | negation 
            event_param: WORDP WORDP
            negation: "!" "(" WORDP WORDP ("," WORDP WORDP)* ")"
            optional: "OPTIONAL" "(" WORDP WORDP ")"
            any_order: "ANY-ORDER" "(" seq_param ("," seq_param)*  ")"

            // WHERE //
            where: "WHERE"i expr

            // Ordering of `or` and `and` is important
            // Used PostgreSQL operator precedence as reference 
            expr: simple_predicate
                | param_predicate
                | or
                | and
                | eq_op
                | "(" (and|or) ")"
                // allows for nesting, rejects (at least some) unnecessary parens
                
            // the expr and the and/or rules are mutually recursive 
            and: expr "AND"i expr
            or: expr "OR"i expr
            
            
            
            simple_predicate: s_eq | s_lt | s_lte | s_gt | s_gte | s_ne
            
            param_predicate: p_eq | p_lt | p_lte | p_gt | p_gte | p_ne
            
            s_eq:     _field_access "="    _literal
            s_lt:     _field_access "<"    _literal
            s_lte:    _field_access "<="   _literal
            s_gt:     _field_access ">"    _literal                
            s_gte:    _field_access ">="   _literal
            s_ne:     _field_access "!="   _literal
            
            p_eq:     _field_access "="    _field_access 
            p_lt:     _field_access "<"    _field_access 
            p_lte:    _field_access "<="   _field_access 
            p_gt:     _field_access ">"    _field_access 
            p_gte:    _field_access ">="   _field_access 
            p_ne:     _field_access "!="   _field_access 
            
            _field_access: WORDP "." WORDP
            _literal: (WORDP | NUMBER)
            
            
            
            eq_op: "[" WORDP "]"

            // WITHIN //
            within: "WITHIN"i INT time_unit
            !time_unit: "HOURS"i | "MINUTES"i | "SECONDS"i

            WORDP: (LETTER|DIGIT|"_"|"-")+

            %import common.WORD   // imports from terminal library
            %import common.DIGIT
            %import common.LETTER
            %import common.NUMBER
            %import common.INT
            %import common.WS
            %ignore WS            // Disregard spaces in text
         ''')


class EventType:

    def __init__(self, name, fields: List[Tuple[str, str]]):
        self.name = name
        self.fields = fields
        
    def __repr__(self):
        field_list = ', '.join(self.fields)
        return f"type {self.name}({field_list})"
    
    @property
    def __dict__(self):
        return {'name': self.name, 'fields': self.__fields_to_dict(self.fields)}
    
    @staticmethod
    def __fields_to_dict(fields: List[Tuple[str, str]]) -> List[Dict[str, str]]:
        return [{"name": f[0], "type": f[1]} for f in fields]

class EventVar:
    
    def __init__(self, event_type, name, order):
        self.event_type = event_type
        self.name = name
        self.negated = False
        self.order = order
        
    def __repr__(self):
        return f"Event({self.event_type}, {self.name}, {self.order})"

    @property
    def __dict__(self):
        return {'event_type': self.event_type, 'name': self.name, 'negated': self.negated, 'order': self.order}

class Negation(EventVar):
    
    def __init__(self, *args, **kwargs):
        super(Negation, self).__init__(*args, **kwargs)
        self.negated = True
    
    def __repr__(self):
        return f"Negation({self.event_type}, {self.name})"


class ParamPredicate:
    OPS = {
        'p_eq': "=",
        'p_ne': "!=",
        'p_gt': ">",
        'p_gte': ">=",
        'p_lt': "<",
        'p_lte': "<="
    }


    def __init__(self, op, left_var, left_field, right_var, right_field):
        self.op = op
        self.left_var = left_var
        self.left_field = left_field
        self.right_var = right_var
        self.right_field = right_field
        
    def __repr__(self):
        return f"ParamPredicate({self.left_var}.{self.left_field} {ParamPredicate.OPS[self.op]} {self.right_var}.{self.right_field})"

    @property
    def __dict__(self):
        return {
                'op': self.op,
                'left_var': self.left_var,
                'left_field': self.left_field,
                'right_var': self.right_var,
                'right_field': self.right_field
            }
        

class SimplePredicate:
    OPS = {
        's_eq': "=",
        's_ne': "!=",
        's_gt': ">",
        's_gte': ">=",
        's_lt': "<",
        's_lte': "<="
    }
    def __init__(self, op, left_var, left_field, literal):
        self.op = op
        self.left_var = left_var
        self.left_field = left_field
        self.literal = literal

    def __repr__(self):
        return f"SimplePredicate({self.left_var}.{self.left_field} {SimplePredicate.OPS[self.op]} {self.literal})"
    
    @property
    def __dict__(self):
        return {
                'op': self.op,
                'left_var': self.left_var,
                'left_field': self.left_field,
                'literal': self.literal
            }

class Sequence:

    def __init__(self, event_seq: List[EventVar]):
        self.event_seq = event_seq

    def __repr__(self):
        es = map(lambda e: repr(e), self.event_seq)
        return f"Seq({','.join(es)})"
    
    @property
    def __dict__(self):
        return {
            'event_seq': [e.__dict__ for e in self.event_seq]
        }

class Within:

    @classmethod
    def empty(cls):
        return cls(0, 'MINUTES')

    def __init__(self, magnitude, unit):
        self.magnitude = magnitude
        self.unit = unit

    def __repr__(self):
        return f"Within({self.magnitude} {self.unit})"
    
    @property
    def __dict__(self):
        return {'magnitude': self.magnitude, 'unit': self.unit}

class Expr:
    

    @property
    def __dict__(self):
        return {
                'op': 'none',
                'left': 'true',
                'right': 'true'
            }

class And(Expr):

    def __init__(self, left, right):
        self.left = left
        self.right = right

    def __repr__(self):
        return f"And({repr(self.left)}, {repr(self.right)})"
    
    @property
    def __dict__(self):
        return {
                'op': 'and',
                'left': self.left.__dict__,
                'right': self.right.__dict__
            }

class Or(Expr):
    def __init__(self, left, right):
        self.left = left
        self.right = right

    def __repr__(self):
        return f"Or({repr(self.left)}, {repr(self.right)})"
    
    @property
    def __dict__(self):
        return {
                'op': 'or',
                'left': self.left.__dict__,
                'right': self.right.__dict__
            }
    

class Where:
    
    def __init__(self, expr_root: Expr):
        self.expr_root = expr_root
    
    @classmethod
    def empty(cls):
        return cls(Expr())

    def __repr__(self):
        return f"Where({repr(self.expr_root)})"
    
    @property
    def __dict__(self):
        return {'expr_root': self.expr_root.__dict__}

class Query:

    def __init__(self, event_clause, where, within):
        self.event_clause = event_clause
        self.where = where
        self.within = within

    def __repr__(self):
        return f"Query({repr(self.event_clause)}, {repr(self.where)}, {repr(self.within)} )"

    @property
    def __dict__(self):
        return {'event_clause': self.event_clause.__dict__, 'where': self.where.__dict__, 'within': self.within.__dict__}

class EqOp:

    def __init__(self, field):
        self.field = field

    def __repr__(self):
        return f"EqOp({self.field})"
    
    @property
    def __dict__(self):
        return {'op': 'eq_op', 'field': self.field}

class EventTypes:

    def __init__(self, event_types: List[EventType]):
        self.event_types = event_types

    def __repr__(self):
        et = map(lambda e: repr(e), self.event_types)
        return f"EventTypes( {','.join(et)} )"
    
    @property
    def __dict__(self):
        return [e.__dict__ for e in self.event_types]

class CEPProgram:

    def __init__(self, event_types: EventTypes, query):
        self.event_types = event_types
        self.query = query

    def __repr__(self):
        return f"CEPProgram({repr(self.event_types)}, {repr(self.query)})"
    
    @property
    def __dict__(self):
        return {'event_types': self.event_types.__dict__, 'query': self.query.__dict__}

class Operator:
    """This is a class to denote an operator and make it distinct from the classes
    that are related to query language constructs. It's just to make the operator classes
    easy to spot."""
    pass

class SEQ_(Operator):
    pass

class SEQ_WITHOUT(Operator):
    pass

class WITHIN_(Operator):
    pass

class ANY(Operator):
    pass

class SELECTION(Operator):
    """This supports the WHERE clause from the query language"""
    pass

class WITHIN_(Operator):
    pass

class CEPVisitor(Interpreter):
    
    def event_def(self, tree):
        assert tree.data == "event_def", tree.data

        event_name = tree.children[0].children[0]
        event_fields = [(str(c.children[0]), str(c.children[1].data) ) for c in tree.children[1].children]

        e = EventType(event_name.value, event_fields)
        return e

    def event_def_list(self, tree):
        assert tree.data == "event_def_list", tree.data
        el = [ self.event_def(c) for c in tree.children ]
        return EventTypes(el)

    def event_param(self, tree, order):
        assert tree.data == "event_param", tree.data
        event_type = tree.children[0].value
        var_name = tree.children[1].value
        e = EventVar(event_type, var_name, order)
        return e
        
    def negation(self, tree, order):
        assert tree.data == 'negation', tree.data
        event_type = tree.children[0].value
        var_name = tree.children[1].value
        n = Negation(event_type, var_name, order)
        return n

    def seq(self, tree):
        assert tree.data == 'seq', tree.data
        
        events = []
        
        for (i, c) in enumerate(tree.children):
            if c.data == 'event_param':
                events.append(self.event_param(c, i))
            elif c.data == 'negation':
                events.append(self.negation(c, i))
            else:
                raise NotImplementedError(f"function not implemented for {c.data} in EVENT clause")
        
        return Sequence(events)

    def event_clause(self, tree):
        assert tree.data == 'event_clause', tree.data
        if tree.children[0].data == 'seq':
            return self.seq(tree.children[0])

    def within(self, tree):
        assert tree.data == "within", tree.data
        mag = int(tree.children[0].value)
        unit = tree.children[1].children[0].value
        w = Within(mag, unit)
        tree.children = [w]
        return w

    def any_order(self, tree):
        assert tree.data == "any_order", tree.data
        

    def expr(self, tree):
        for c in tree.children:
            if c.data == 'and':
                return self.and_op(c)
            elif c.data == 'or':
                return self.or_op(c)
            elif c.data == 'eq_op':
                return self.eq_op(c)
            elif c.data == 'param_predicate':
                return self.param_pred(c)
            elif c.data == 'simple_predicate':
                return self.simple_pred(c)
            elif c.data == 'expr':
                return self.expr(c)
            else:
                raise NotImplementedError(f"No function implemented to handle {c.data} in WHERE clause")

    def simple_pred(self, tree):
        assert tree.data == "simple_predicate", tree.data
        op = tree.children[0].data
        left_var = tree.children[0].children[0]
        left_field = tree.children[0].children[1]
        right_literal = tree.children[0].children[2]

        s = SimplePredicate(op, left_var, left_field, right_literal)
        return s

    def param_pred(self, tree):
        assert tree.data == "param_predicate", tree.data
        op = tree.children[0].data
        left_var = tree.children[0].children[0].value
        left_field = tree.children[0].children[1].value
        right_var = tree.children[0].children[2].value
        right_field = tree.children[0].children[3].value

        p = ParamPredicate(op, left_var, left_field, right_var, right_field)
        return p

    def eq_op(self, tree):
        assert tree.data == "eq_op", tree.data
        field = tree.children[0].value
        e = EqOp(field)
        return e

    def and_op(self, tree):
        assert tree.data == "and", tree.data
        sub = [self.expr(c) for c in tree.children]
        a = And(sub[0], sub[1])
        return a

    def or_op(self, tree):
        assert tree.data == "or", tree.data
        sub = [self.expr(c) for c in tree.children]
        o = Or(sub[0], sub[1])
        return o


    def where(self, tree):
        assert tree.data == "where", tree.data
        """
        The structure of the WHERE clause is the most complicated so far AND it has to
        support sub-expressions which add to the complexity so we have to be very
        disciplined and methodical about how we write this code.

        We're going to look at the parsing rules associated with the where clause and we're going to have
        one function to handle each parsing rule until we get to the leaf nodes or a place where sub-expressions
        aren't allowed, such as the comparision operators.
        """
        expr_root = self.expr(tree)
        w = Where(expr_root)
        return w


    def query(self, tree):
        _event = tree.children[0]
        
        where = Where.empty()
        if len(tree.children) > 1:
            _where = tree.children[1]
            where = self.where(_where)
        within = Within.empty()
        if len(tree.children) > 2:
            _within = tree.children[2]
            within = self.within(_within)
        event = self.event_clause(_event)
        
        
        q = Query(event, where, within)
        return q



    def __default__(self, tree):
        event_def_list = tree.children[0]
        query = tree.children[1]
        e = self.event_def_list(event_def_list)
        q = self.query(query)
        return CEPProgram(e, q)

def leaf_to_pretty(leaf):
    if 'SimplePredicate' in leaf:
        pred = leaf['SimplePredicate']
        op = pred['op']
        if op == "s_eq":
            return f"{pred['left_var']}.{pred['left_field']} = {pred['literal']}"
        elif op == "s_ne":
            return f"{pred['left_var']}.{pred['left_field']} != {pred['literal']}"
        elif op == "s_gt":
            return f"{pred['left_var']}.{pred['left_field']} > {pred['literal']}"
        elif op == "s_gte":
            return f"{pred['left_var']}.{pred['left_field']} >= {pred['literal']}"
        elif op == "s_lt":
            return f"{pred['left_var']}.{pred['left_field']} < {pred['literal']}"
        elif op == "s_lte":
            return f"{pred['left_var']}.{pred['left_field']} <= {pred['literal']}"


    elif 'ParamPredicate' in leaf:
        pred = leaf['ParamPredicate']
        op = pred['op']
        if op == "p_eq":
            return f"{pred['left_var']}.{pred['left_field']} = {pred['right_var']}.{pred['right_field']}"
        elif op == "p_ne":
            return f"{pred['left_var']}.{pred['left_field']} != {pred['right_var']}.{pred['right_field']}"
        elif op == "p_gt":
            return f"{pred['left_var']}.{pred['left_field']} > {pred['right_var']}.{pred['right_field']}"
        elif op == "p_gte":
            return f"{pred['left_var']}.{pred['left_field']} >= {pred['right_var']}.{pred['right_field']}"
        elif op == "p_lt":
            return f"{pred['left_var']}.{pred['left_field']} < {pred['right_var']}.{pred['right_field']}"
        elif op == "p_lte":
            return f"{pred['left_var']}.{pred['left_field']} <= {pred['right_var']}.{pred['right_field']}"
    else:
        return ""

def json_to_pretty(json):
    # import pdb;pdb.set_trace()
    pretty_types = ""
    for et in json['event_types']:
        name = et['name']
        fields = et['fields']
        pretty_types += f"type {name}({','.join(fields)})\n"

    es = json['query']["event_clause"]['event_seq']

    pretty_events = []
    for event in es:
        if event['negated'] is False:
            pretty_events.append(f"{event['event_type']} {event['name']}")
        else:
            pretty_events.append(f"!({event['event_type']} {event['name']})")
    pretty_event_clause = f"EVENT SEQ({','.join(pretty_events)})"
    pretty_where_clause = ""
    if 'where' in json['query'] and json['query']['where'] is not None:
        # import pdb;pdb.set_trace()
        pretty_where_clause = "WHERE "
        where = json['query']['where']['expr_root']
        if 'SimplePredicate' in where:
            pretty_where_clause += leaf_to_pretty(where)
        elif 'ParamPredicate' in where:
            pretty_where_clause += leaf_to_pretty(where)
        elif 'And' in where:
            pred = where['And']
            op = pred['op']
            if op == "and":
                left = pred['left']
                right = pred['right']

                pretty_where_clause += f"{leaf_to_pretty(left)} AND {leaf_to_pretty(right)}"
        elif 'Or' in where:
            pred = where['Or']
            op = pred['op']
            if op == "or":
                left = pred['left']
                right = pred['right']

                pretty_where_clause += f"{leaf_to_pretty(left)} OR {leaf_to_pretty(right)}"
    pretty_within_clause = ""
    if 'within' in json['query'] and json['query']['within'] is not None:
        pretty_within_clause = "WITHIN "
        within = json['query']['within']
        unit = within['unit']
        magnitude = within['magnitude']
        pretty_within_clause += f"{magnitude} {unit}"
    pretty_query = f"{pretty_types.lstrip()}\n{pretty_event_clause}\n{pretty_where_clause}\n{pretty_within_clause}".lstrip()
    return pretty_query



words = """
type A(id: INTEGER, field1: STRING, field2: STRING)
type B(id: INTEGER, field1: STRING, field2: STRING)
type C(id: INTEGER, field1: STRING, field2: STRING)
type D(id: INTEGER, field1: STRING, field2: STRING)
   
EVENT SEQ(A a, !(C c),  D d)
WHERE a.field1 = 100 AND d.field1 = a.field1
WITHIN 5 HOURS
"""    



if __name__ == "__main__":
    
    tree = l.parse(words)    

    r = CEPVisitor().visit(tree)
    e = json.dumps(r.__dict__)
    print( e )
