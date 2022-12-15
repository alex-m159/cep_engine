
<!-- 
<p align="center">
  <a href="https://nodejs.org/">
    <img
      alt="French Toast"
      src="https://frenchtoastassests.us-east-1.linodeobjects.com/brandmark-design-600x0.png"
      width="600"
    />
  </a>
</p>
-->
<h2>Event Oven</h2>

Event Oven is a partial implementation of the language and query engine described in [High-Performance Complex Event Processing Over Streams](http://cs.brown.edu/courses/cs295-11/2007/complex.pdf).

The query language allows users to define a set of event types and a sequence or pattern of those events. The query engine will take the query and will start reading from the input stream; once it find a series of events that match the given pattern and pass all constraints, it will produce that as output.

Here's an example of a possible query:

```text
type A(id, field1, field2)
type B(id, field1, field2)
type C(id, field1, field2)
type D(id, field1, field2)
   
EVENT SEQ(A a, !(C c),  D d)
WHERE a.field1 = 100
WITHIN 5 HOURS
```
The `EVENT SEQ(A a, !(C c),  D d)` define the order of the pattern. It includes a negation, meaning that the event `c` should not occur between events `a` and `b` in the stream for any matches produced by the query.

The `WHERE` clause defines predicates on the events in the `EVENT` clause and support comparisons (=, >, <, etc) and boolean operators (AND/OR). 

The `WITHIN` clause provide a window or time bound; elements that are more than 5 hours old will no longer be considered for query matches. 

## Example Stream

Let's say that we're running the query above and we get the following events as input:

```text
A(1, 100, 200)
C(2, 200, 300)
D(3, 200, 300)
A(4, 100, 200)
D(5, 200, 300)
```
The output would be: 
  `A(4, 100, 200), D(5, 200, 300)`
The first A-D pair are not produced as output since an event of type C comes in between them.

## Use Cases
The linked paper provides a few use cases, focusing on retail and healthcare settings. In general, the use cases focus on alerting for relatively complex situations that require several different events to be identified and grouped together into a higher-level, more abstract event. While you could use it to make simple, threshold based alerts (i.e. an alert for is a server uses >80% of its disk space), it's better suited for more complex cases that span at least two individual events.

## Implementation
There are a few different components involved in query processing: the parser (used to parse the query language/DSL), the query planner, and the query interpreter. The parser is implemented in Python using the Lark package, which is a parser generator that accepts a definition of the language as a set of rules. 
All the other components, which I typically refer to as the "query engine" or the "query backend", are written in Scala. 

For data storage and event/message transport we use Kafka exclusively at the moment. It would be possible to use any type of message queue that supported ordering across all the events for a given queue/stream/topic. In the case of Kafka, since we need all the events in a topic to be ordered, it's required that each topic has only one partition, and we have one topic per running query.

In addition to the backend components, there's also a web interface that makes use of Flask. It allows you to view the currently running queries, "connect to" a currently running query (i.e. subscribe to the query output and have that output send to the browser), to send events to a query, and to delete a query. 
The web UI doesn't provide any essential features related to query processing, but it makes interacting with the query processing parts a lot easier.

