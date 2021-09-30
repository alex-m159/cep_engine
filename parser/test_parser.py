from parser import l
import pytest
from lark import UnexpectedCharacters


class TestSingleParam:
    def test_no_param(self):
        with pytest.raises(UnexpectedCharacters) as e:
            t = "EVENT SEQ()"
            l.parse(t)
    def test_only_event_exp(self):
        t1 = """EVENT SEQ(A a)"""
        l.parse(t1)
        
        # Check that space between SEQ and ( is accepted
        t1 = """EVENT SEQ ( A a )"""
        l.parse(t1)

    def test_event_where_exp(self):
        t2 = """
        EVENT SEQ(A a) 
        WHERE a.id = 1
        """
        l.parse(t2)

    def test_event_within_exp(self):
        t = """
        EVENT SEQ(A a)
        WITHIN 12 HOURS
        """
        l.parse(t)
        
    def test_event_where_within_exp(self):
        t = """
        EVENT SEQ(A a)
        WHERE a.value > 12
        WITHIN 12 HOURS
        """
        l.parse(t)
        
        
class TestEventSection:
    def test_single_param(self):
        t = "EVENT SEQ(A a)"
        l.parse(t)
        
    def test_single_param_no_paren(self):
        t = "EVENT SEQ A a"
        with pytest.raises(UnexpectedCharacters) as e:
            l.parse(t)
            
    def test_multi_param(self):
        t = "EVENT SEQ(A a, B b)"
        l.parse(t)
        
        t = "EVENT SEQ(A a, B b, C c, D d, E e, F f, G g, H h)"
        l.parse(t)
        
        with pytest.raises(UnexpectedCharacters) as e:
            t = "EVENT SEQ A a, B b, C c"
            l.parse(t) 
    def test_single_negate_param(self):
        t = "EVENT SEQ(!(A a))"
        l.parse(t)
        
    def test_multi_negate_param(self):
        t = "EVENT SEQ(!(A a, B b, C c))"
        l.parse(t)
        # Check that spaces are accepted
        t = "EVENT SEQ ( !( A a , B b , C c ) )"
        l.parse(t)
        
    def test_mixed_negate_param(self):
        t = "EVENT SEQ( A a, !(B b), C c, !(D d), E e)"
        l.parse(t)

class TestWhereSection:
    def test_single_simple_pred(self):
        # Simple predicate
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field = 1
        """
        l.parse(t)
        
        # Parameterized predicate
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field = b.field1
        """
        l.parse(t)
        
        # Equality operator
        t = """
        EVENT SEQ(A a, B b)
        WHERE [field]
        """
        l.parse(t)
        
    def test_many_simple_pred(self):
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field > 1 AND b.field <= 500
        """
        l.parse(t)
    
        t = """
        EVENT SEQ(A a, B b, C c)
        WHERE a.field_1 > 1 AND b.field_2 < 500 OR c.field3 = 400
        """
        l.parse(t)
        
    def test_compound_complex_pred(self):
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field > 1 AND b.field <= a.field2
        """
        l.parse(t)
    
        t = """
        EVENT SEQ(A a, B b, C c)
        WHERE [id] AND a.field_1 > 1 AND b.field_2 < 500 OR c.field3 = b.field4
        """
        l.parse(t)
    
    def test_pred_with_parens(self):
        t = """
        EVENT SEQ(A a, B b, C c)
        WHERE a.field_1 > 1 AND (b.field_2 < 500 OR c.field3 = 400)
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b, C c)
        WHERE (a.field_1 > 1 AND b.field_2 < 500) OR c.field3 = 400
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE (a.field_1 > 1 AND b.field_2 < 500) OR (c.field3 = 400 AND d.field4 != 20)
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE ((a.field_1 > 1 AND b.field_2 < 500) OR (c.field3 = 400 AND d.field4 != 20))
        """
        l.parse(t)
        
        with pytest.raises(UnexpectedCharacters) as e:
            t = """
            EVENT SEQ(A a, B b, C c)
            WHERE (a.field_1 > 1 AND b.field_2 < 500) OR (c.field3 = 400)
            """
            l.parse(t)
    
    def test_pred_with_nested_conditions(self):
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE (a.field_1 > 1 AND (b.field_2 < 500 OR (c.field3 = 400 AND d.field4 != 20)))
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE ((b.field_2 < 500 OR (c.field3 = 400 AND d.field4 != 20)) AND a.field_1 > 1)
        """
        l.parse(t)
        
        with pytest.raises(UnexpectedCharacters) as e:
            t = """
            EVENT SEQ(A a, B b, C c, D d)
            WHERE ((b.field_2 < 500 OR (c.field3 = 400 AND d.field4 != 20)))
            """
            l.parse(t)

    
    def test_complex_pred_with_parens(self):
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field > 1 AND b.field <= a.field2
        """
        l.parse(t)
    
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE [id]
            AND (a.field_1 > 1 AND b.field_2 < 500) 
            OR (c.field3 = b.field4 AND c.field4 != d.field4)
        """
        l.parse(t)
        
        with pytest.raises(UnexpectedCharacters) as e:
            # Fails due to extra parens around [id]
            t = """
            EVENT SEQ(A a, B b, C c, D d)
            WHERE ([id])
                AND (a.field_1 > 1 AND b.field_2 < 500) 
                OR (c.field3 = b.field4 AND c.field4 != d.field4)
            """
            l.parse(t)
     
    def test_complex_pred_nest_conditions(self):
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE 
            (a.field_1 > c.field_3 
                AND( 
                    b.field_2 < 500 
                    OR 
                    (c.field3 = 400 AND d.field4 != 20)
                    OR
                    [field_123]
                )
            ) OR [field_456]
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE 
            (a.field_1 > c.field_3 
                AND( 
                    b.field_2 < 500 
                    OR 
                    (c.field3 = 400 AND d.field4 != 20)
                    OR
                    [field_123]
                )
            ) 
        OR 
            ([field_456] AND b.field5 != 10)
        """
        l.parse(t)
        
        with pytest.raises(UnexpectedCharacters) as e:
            # Fails due to unnecessary parentheses around
            # last predicate
            t = """
            EVENT SEQ(A a, B b, C c, D d)
            WHERE 
                (a.field_1 > c.field_3 
                    AND( 
                        b.field_2 < 500 
                        OR 
                        (c.field3 = 400 AND d.field4 != 20)
                        OR
                        [field_123]
                    )
                ) 
            OR 
                (([field_456] AND b.field5 != 10))
            """
            l.parse(t)

class TestWithinSection:

    def test_within_alone(self):
        t = """
        EVENT SEQ(A a, B b)
        WITHIN 12 HOURS
        """
        l.parse(t)
        
        t = """
        EVENT SEQ(A a, B b)
        WITHIN 12 MINUTES
        """
        l.parse(t)

        t = """
        EVENT SEQ(A a, B b)
        WITHIN 12 SECONDS
        """
        l.parse(t)
        
        
    def test_within_and_where(self):
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field1 = b.field2
        WITHIN 12 HOURS
        """
        l.parse(t)
    
        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field1 = b.field2
        WITHIN 12 MINUTES
        """
        l.parse(t)

        t = """
        EVENT SEQ(A a, B b)
        WHERE a.field1 = b.field2
        WITHIN 12 SECONDS
        """
        l.parse(t)
        
    def test_complicated_within_statement(self):
        t = """
        EVENT SEQ(A a, B b, C c, D d)
        WHERE 
            (a.field_1 > c.field_3 
                AND( 
                    b.field_2 < 500 
                    OR 
                    (c.field3 = 400 AND d.field4 != 20)
                    OR
                    [field_123]
                )
            ) 
        OR 
            ([field_456] AND b.field5 != 10)
        WITHIN 60 MINUTES
        """
        l.parse(t)
        
    def test_decimal_within(self):
        with pytest.raises(UnexpectedCharacters) as e:
            t = """
            EVENT SEQ(A a, B b)
            WITHIN 1.5 MINUTES
            """
            l.parse(t)
    
    
    
    
    
    
class TestEventDefinition:
    
    def test_basic_syntax(self):
        t = "type EventOne(id, field1, field_2, field_three)"
        l.parse(t)
        
        t = """
        type EventOne(Id, Field1, Field_2, field_three)
        type Event2(_id, 1field, 2)
        """
        l.parse(t)
        
    def test_bad_syntax(self):
        with pytest.raises(UnexpectedCharacters) as e:
            # Shouldn't have ending semicolon
            t = """
            type EventOne(id, field1, field_2, field_three);
            type Event2(id, _1field, _2);
            """
            l.parse(t)
            
        with pytest.raises(UnexpectedCharacters) as e:
            # Extra comma at end
            t = """
            type EventOne(id, field1, field_2, field_three,)
            type Event2(id, _1field, _2)
            """
            l.parse(t)
            
        with pytest.raises(UnexpectedCharacters) as e:
            # Missing commas between fields
            t = """
            type EventOne(id field1 field_2 field_three)
            type Event2(id _1field _2)
            """
            l.parse(t)
            
       
class TestFullQuery:

    def test_simple_query(self):
        t = """
        type One(id, field1, field2)
        type Two(id, field1, field2)
        
        EVENT SEQ(One o, Two t)
        WHERE o.field1 = t.field2
        WITHIN 12 HOURS
        """        
        l.parse(t).pretty()
     
    
    
    
    
    
    
    
    
    
    
 
