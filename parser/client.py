from socket import socket
from flask import Flask, render_template, jsonify, request
from cep_parser import l, CEPVisitor, words, json_to_pretty
from flask_socketio import SocketIO, emit, join_room, leave_room
import json
import os
from threading import Thread

from requests import post, get
from kafka import KafkaConsumer, TopicPartition, KafkaProducer, errors
from collections import defaultdict
import time
from logging import Logger
from typing import Optional, Any, List, Dict, Tuple
import os
from lark.exceptions import UnexpectedEOF


from flask_cors import CORS, cross_origin

logger = Logger("cep console")

def submit_query(query_string):
    tree = l.parse(query_string)
    visited = CEPVisitor().visit(tree)
    json_query = json.dumps(visited.__dict__) + os.linesep
    print(json_query)
    resp = post('http://localhost:8000/query', data=json_query)    
    print(resp.text)
    j = json.loads(resp.text)
    if j['ok']:
        return j['query_id']
    else:
        return False

def get_plan(query_string):
    tree = l.parse(query_string)
    visited = CEPVisitor().visit(tree)
    json_query = json.dumps(visited.__dict__) + os.linesep

    resp = post('http://localhost:8000/query/plan', data=json_query)
    return json.loads(resp.text)['plan']

def get_ast(query_string):
    tree = l.parse(query_string)
    visited = CEPVisitor().visit(tree)
    ast = visited.__dict__
    return ast

def get_all_queries():
    resp = get('http://localhost:8000/query')
    parsed = json.loads(resp.text)
    return parsed['queries']


def delete_query(query_string):
    tree = l.parse(query_string)
    visited = CEPVisitor().visit(tree)
    json_query = json.dumps(visited.__dict__) + os.linesep
    # print(json_query)
    resp = post('http://localhost:8000/query/delete', data=json_query)
    print("Delete Query response:")
    print(resp.text)
    try:
        json_res = json.loads(resp.text)
        if json_res['ok']:
            return True
        return False
    except json.JSONDecodeError:
        logger.error("Response from CEP engine was not valid JSON and could not be parsed")
        return False

query_raw = words

app = Flask(__name__)
app.config['SECRET_KEY'] = 'secret!'
app.config['JSONIFY_PRETTYPRINT_REGULAR'] = False
socketio = SocketIO(app, cors_allowed_origins="*")

CORS(app)
# Map room names to threads
emit_threads: Dict[str, Thread] = {}


@app.route('/', methods=['GET'])
def root():
    all_q = get_all_queries()
    queries = [{'query_id': query_id, 'query_pretty': json_to_pretty(query_ast)} for query_id, query_ast in all_q.items()]
    return render_template('query_list.html', queries=queries)

@app.route('/query/create', methods=['GET'])
def query_create():
    return render_template('main.html')

class NoPartitions(Exception):
    pass

def get_partitions(consumer: KafkaConsumer, topic) -> List[TopicPartition]:
    # import pdb;pdb.set_trace()
    part_ids = consumer.partitions_for_topic(topic)
    if part_ids is None:
        raise NoPartitions()
    assert len(part_ids) > 0
    if part_ids:
        return [TopicPartition(topic, part_id) for part_id in part_ids]
    return []


def get_beginning_offset(consumer: KafkaConsumer, topic) -> Optional[int]:
    """ Returns the last offset that exists, which is in contrast to the KafkaConsumer.end_offsets().
        end_offsets() will return the offset that the next incoming message will be given, not the highest
        assigned offset.
    """
    top_parts = get_partitions(consumer, topic)
    begin_offsets = consumer.beginning_offsets(top_parts).values()
    if len(begin_offsets) > 0:
        return min(begin_offsets)
    return None

def get_end_offset(consumer: KafkaConsumer, topic) -> Optional[int]:
    """ Returns the last offset that exists, which is in contrast to the KafkaConsumer.end_offsets().
        end_offsets() will return the offset that the next incoming message will be given, not the highest
        assigned offset.
    """
    top_parts = get_partitions(consumer, topic)
    end_offsets = consumer.end_offsets(top_parts).values()
    if len(end_offsets) > 0:
        highest_offset = max(end_offsets) - 1 # need minus 1
        if highest_offset < 0:
            # If there's no data in the topic, the -1 will make the returned offset
            # zero, so we just return null instead for API consistency
            return None
        return highest_offset
    return None


def relative_seek(consumer: KafkaConsumer, topic, back=50):
    """Seeks the consumer back a relative number of offsets from the current offset"""
    partitions = make_topic_partitions(topic, 1)
    consumer.assign(partitions)
    end_offset = get_end_offset(consumer, topic)
    begin_offset = get_beginning_offset(consumer, topic)

    if end_offset is None or begin_offset is None:
        return []

    partitions = get_partitions(consumer, topic)
    assert len(partitions) == 1, "Output Kafka topic does not have exactly one partition!"
    partition = partitions[0]

    offset_diff = min(back, abs(end_offset - begin_offset))
    start_from = end_offset - offset_diff
    consumer.seek(partition, start_from)

def get_beginning_and_end(consumer: KafkaConsumer, topic: str) -> Tuple[int, int]:
    end_offset = get_end_offset(consumer, topic)
    begin_offset = get_beginning_offset(consumer, topic)
    return (begin_offset, end_offset)

def get_message_subset(consumer: KafkaConsumer, topic: str, earliest=None, latest=None) -> List[Any]:
    end_offset = get_end_offset(consumer, topic)
    begin_offset = get_beginning_offset(consumer, topic)

    if end_offset is None or begin_offset is None:
        return []

    partitions = get_partitions(consumer, topic)
    assert len(partitions) == 1, "Output Kafka topic does not have exactly one partition!"
    partition = partitions[0]

    
    
    # import pdb;pdb.set_trace()
    # consumer.poll()
    consumer.seek(partition, earliest)
    messages = []
    curr_offset = earliest
    print(f"start_from: {earliest} - end_offset: {latest} on topic {topic}")
    print(f"Attempting to get {latest - earliest + 1} most recent messages from Kafka")
    temp = defaultdict(list)
    try:
        while curr_offset < latest:
            logger.info("Looping in loop")
            records = consumer.poll(3000)
            
            print(f"records count: {len(records)}")
            # import pdb;pdb.set_trace()
            for (topic_part, msg_list) in records.items():
                print(f"Partition: {topic_part}")
                named = [{
                    'match': msg.value,
                    'offset': msg.offset,
                    'timestamp': msg.timestamp
                } for msg in msg_list]
                temp[topic].extend(named)
            assert topic in temp

            ordered_msgs = list(sorted(temp[topic], key=lambda m: m['offset']))
            filtered = filter(lambda m: earliest <= m["offset"] and m["offset"] <= latest, ordered_msgs)
            if ordered_msgs:
                messages.extend(filtered)
            curr_offset = ordered_msgs[-1]['offset']
        logger.info(f"Got recent messages from Kafka")
        return messages

    except Exception as ex:
        logger.error("Could not get recent messages from Kafka", exc_info=True)
        return []

def get_recent_messages(consumer: KafkaConsumer, topic: str, most_recent: int = 50) -> List[Any]:
    """
    This function assumes that the Kafka topic has only one partition, meaning that the messages
    are fully ordered within that topic.

    :param consumer:
    :param topic:
    :param most_recent:
    :return:
    """
    # import pdb;pdb.set_trace()

    end_offset = get_end_offset(consumer, topic)
    begin_offset = get_beginning_offset(consumer, topic)

    if end_offset is None or begin_offset is None:
        return []

    partitions = get_partitions(consumer, topic)
    assert len(partitions) == 1, "Output Kafka topic does not have exactly one partition!"
    partition = partitions[0]

    offset_diff = min(most_recent, abs(end_offset - begin_offset))
    start_from = end_offset - offset_diff
    # import pdb;pdb.set_trace()
    # consumer.poll()
    consumer.seek(partition, start_from)
    messages = []
    curr_offset = start_from
    print(f"start_from: {start_from} - end_offset: {end_offset}")
    print(f"Attempting to get {offset_diff} most recent messages from Kafka")
    try:
        while curr_offset < end_offset:
            logger.info("Looping in loop")
            records = consumer.poll(3000)
            temp = defaultdict(list)
            print(f"records count: {len(records)}")
            # import pdb;pdb.set_trace()
            for (topic_part, msg_list) in records.items():
                print(f"Partition: {topic_part}")
                named = [{
                    'match': msg.value,
                    'offset': msg.offset,
                    'timestamp': msg.timestamp
                } for msg in msg_list]
                temp[topic_part.topic].extend(named)

            assert topic in temp

            ordered_msgs = sorted(temp[topic], key=lambda m: m['offset'])
            curr_offset = ordered_msgs[-1]['offset']
            messages.extend(ordered_msgs)
        logger.info(f"Got recent messages from Kafka")
        return messages

    except Exception as ex:
        logger.error("Could not get recent messages from Kafka", exc_info=True)
        return []

def send_data(query_id, cons: KafkaConsumer = None):
    ws_room = room_name(query_id)
    consumer = cons or KafkaConsumer(f"output-{query_id}", group_id=f'query-{query_id}')
    print(f"Reading from topic output-{query_id}, consumer group: query-{query_id}")
    while True:
        matches = consumer.poll(1000)
        if matches:
            print("got query match")
            print(matches)
            data_list = [{'value': rec.value.decode(), 'offset': rec.offset} for rec in condense_data(matches)[f'output-{query_id}']]
            socketio.emit("cep_match", {'data': data_list}, room=ws_room)
        time.sleep(4)

def update_offsets(query_id, cons: KafkaConsumer, emit_fn):
    topic = f"output-{query_id}"
    while True:
        end_offset = get_end_offset(cons, topic)
        begin_offset = get_beginning_offset(cons, topic)
        emit_fn("offset_range", [begin_offset, end_offset])
        time.sleep(4)

def room_name(query_id) -> str:
    return f"query-{query_id}"

# @socketio.on("history")
# def query_history(event):
#     logger.info("Received history message from client")
#     consumer = KafkaConsumer(group_id=f'consumer-{time.time()}')
#     relative_seek(consumer, "output", back=50)
#     room = room_name(event['query_id'])
#     join_room(room)
#     if room not in emit_threads:
#         t = Thread(target=send_data, args=(event['query_id'], consumer))
#         emit_threads[room] = t
#         emit_threads[room].start()

@socketio.on("stream_range")
def check_stream(event):
    try:
        logger.info("====== Received stream_range from client ========")
        consumer = KafkaConsumer(group_id=f'consumer-{time.time()}', value_deserializer=lambda msg: json.loads(msg))
        topic = f"output-{event['query_id']}"
        topic_part = TopicPartition(topic, 0)
        consumer.assign([topic_part])
        offset_range = get_beginning_and_end(consumer, topic)
        print(f"Offset range: {offset_range}")
        emit("offset_range", [offset_range[0], offset_range[1]])
        t = Thread(target=update_offsets, name=f"thread-{event['query_id']}", args=(event["query_id"], consumer, emit))
        t.run()
        emit_threads[f"offsets-{event['query_id']}"] = t
    except errors.NoBrokersAvailable:
        logger.error(f"No brokers available. Using localhost:9092 as broker address.")
        emit("error", "No brokers available.")
    except NoPartitions:
        emit("error", "No output from query.")

@socketio.on('read_stream')
def read_stream(event):
    try:
        print("=== Received read_stream from client === ")
        consumer = KafkaConsumer(group_id=f'consumer-{time.time()}', value_deserializer=lambda msg: json.loads(msg))
        topic = f"output-{event['query_id']}"
        topic_part = TopicPartition(topic, 0)
        consumer.assign([topic_part])
        for m in sorted(get_message_subset(consumer, topic, event["earliest"], event["latest"]), key=lambda m: int(m["offset"])):
            emit("cep_match", m)
        consumer.close()
    except NoPartitions:
        emit("error", "No output from query.")


@app.route('/query/<qid>', methods=['GET'])
def show_query(qid):
    all_q = get_all_queries()
    if qid in all_q:
        q = json_to_pretty(all_q[qid])
    else:
        q = f"Query unavailable. Query ID {qid} does not exist."
    print(f"QUERY QID IS: {qid}")
    return render_template('query_display.html', query=q, query_id=qid)

@app.route('/query', methods=['GET'])
def all_queries():
    queries = get_all_queries()
    pretty = [{'query_string': json_to_pretty(query_ast), 'query_id': query_id} for query_id, query_ast in queries.items()]
    # for p in pretty:
    #     print(p)
    print("Returning all queries")
    return jsonify({"queries": pretty})


@app.route('/query', methods=['POST', 'PUT'])
def query():
    print(request.json)
    query_string = request.json['query']
    # global query_raw
    # query_raw = words
    try:
        if query_string:
            query_id = submit_query(query_string)
            if query_id:
                return jsonify({'ok': 1, 'query_id': query_id})
    except UnexpectedEOF as e:
        return jsonify({'ok': 0, 'err': str(e)})

    return jsonify({'ok': 0})

@app.route('/query/delete', methods=['POST'])
def query_delete():
    query_string = request.json['query']
    if query_string:
        if delete_query(query_string):
            return jsonify({'ok': 1})
    return jsonify({'ok': 0})



@app.route("/query/plan", methods=["POST"])
def query_plan():
    query_string = request.json['query']
    plan = get_plan(query_string)
    return jsonify({'ok': 1, 'plan': plan})

@app.route("/query/ast", methods=["POST"])
def query_ast():
    if 'query' in request.json:
        query_string = request.json['query']
        ast = get_ast(query_string)
        return jsonify({'ok': 1, 'ast': ast})
    elif 'query_id' in request.json:
        qid = request.json['query_id']
        queries = get_all_queries()
        print(str(qid))
        print(queries[str(qid)])
        if str(qid) in queries:
            ast = queries[str(qid)]
            return jsonify({'ok': 1, 'ast': ast})
        else:
            return jsonify({'ok': 0, 'err': "Query ID does not exist or is not associated with active query"})

def send_event(qid: int, event: Tuple[str, List[Dict[str, int]]]):
    p = KafkaProducer(bootstrap_servers='localhost')
    data = {
        'event_type': event[0],
        'fields': event[1]
    }
    print(f"Sending event to query {qid}")
    p.send(f"query_input_{qid}", key=None, value=json.dumps(data).encode())
    p.flush()
    print(f"Flushed events for query {qid}")



@app.route("/query/send_events", methods=["POST"])
def query_send_events():
    print(request.json)
    qid = int(request.json['query_id'])
    for e in request.json['events']:
        event_type, event_fields = tuple(e)
        event_fields = [ {'field_name': field['field_name'], 'field_value': int(field['field_value'])} for field in event_fields ]
        print("About to send events")
        send_event(qid, (event_type, event_fields))
    return jsonify({'ok': 1})

@socketio.on('connect')
def connect_ws(auth):
    print("Connected on WebSocket!")
    print(f"auth: {auth}")
    emit("my response", {'data': 'Connected'})

@socketio.on('disconnect')
def disconnect_ws():
    print("Disconnected on WebSocket!")



def make_topic_partitions(topic_name, partitions):
    return [TopicPartition(topic_name, part) for part in range(partitions)]

def condense_data(data):
    """Accepts a dictionary of type Dict[TopicPartition, List[ConsumerRecord]]
    and marges keys/TopicPartitions belonging to the same topic to give

    type Topic = str # type alias
    Dict[Topic, List[ConsumerRecord]]

    """
    result = defaultdict(list)
    for tp in data.keys():
        result[tp.topic].extend(data[tp])
    return result


@socketio.on("test_event")
def test_event_ws(event):
    print("Received test_event message from client")
    emit("test_event", {"data": "test data"})
    # room = room_name(event['query_id'])
    # join_room(room)
    # if room not in emit_threads:
    #     t = Thread(target=send_data, args=(event['query_id'],))
    #     emit_threads[room] = t
    #     emit_threads[room].start()



"""
type A()
type B()
type C()
type D()
type E()
type F()

A B C D
A B1.1 B1.2 C2.1 C2.2 D
EVENT SEQ(A, SEQ(B, C), D)
CONTIGUOUS
WHERE [token]

"""

if __name__ == "__main__":
    app.run(debug=True, host='0.0.0.0')
