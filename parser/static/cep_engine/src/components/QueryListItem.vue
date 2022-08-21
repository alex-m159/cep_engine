<script setup lang="ts">

import {ref, onMounted, onUnmounted} from 'vue'
import type {Ref} from 'vue'
import type {Query} from '../stores/query'
import {queryResultStore} from '../stores/queryResult'
import type {QueryResultStoreT, CEPMatch} from '../stores/queryResult'
import {backendIp} from '../config'
import { io, Socket} from "socket.io-client"
import type { DefaultEventsMap, EventNames, EventParams, EventsMap, Emitter } from "@socket.io/component-emitter";


interface Props {
    query: Query
}

interface ServerToClient {
    noArg: () => void
    cep_match: (a: CEPMatch) => any
    test_event: (a: any) => any
    offset_range: (a: [number, number]) => any
}

interface ClientToServer {
    hello: () => void,
    test_event: (data: string) => void 
    read_stream: (qid: any) => any
    stream_range: (qid: any) => any
}

interface InterServerEvents {
    ping: () => void
}

interface SocketData {
    something: string, 
    else: number
}

let socket: Ref<Socket<ServerToClient, ClientToServer> | null> = ref(null)

let match_list = ref([{match: "adslasd", offset: 0, timestamp: 100000000}] as Array<CEPMatch>)


let min_offset = ref(0)
let max_offset = ref(0)

onMounted(() => {
    console.log("Query List Item mounted")
    if(socket.value == null ) {
        socket.value = io("ws://localhost:5000/")
    } else if( ! socket.value.connected) {
        console.log("Connecting to WebSocket")
        socket.value.connect()
    }

    
    socket.value.on("noArg", () => {
        console.log("received No Arg Event")
    })
    socket.value.on("test_event", (e) => {
        console.log("Received test event from server")
    })
    socket.value.on("cep_match", (match: CEPMatch) => {
        console.log("Socket IO recieved CEP MATCH")
        console.log(JSON.stringify(match))
        queryResultStore.push(props.query.queryId, match)
        console.log("Updating match_list:")
        console.log(JSON.stringify(queryResultStore.asMap.get(props.query.queryId)))
        match_list.value = 
        Array.from(new Set((queryResultStore.asMap.get(props.query.queryId) as Array<CEPMatch>)
        .values())
        .values())
        .sort((a: CEPMatch, b: CEPMatch) => {
            if(a.offset < b.offset) {
                return -1
            } else if(a.offset === b.offset) {
                return 0
            } else {
                return 1
            }
        }).reverse()
        let visible_offsets = match_list.value.map((match) => match.offset)
        console.log(`Visible offsets: ${visible_offsets.length}`)
        min_offset.value = Math.min.apply(null, visible_offsets);
        max_offset.value = Math.max.apply(null, visible_offsets);
        console.log(`Min offset: ${min_offset}, max offset: ${max_offset}`)
    })

    socket.value.on("offset_range", (range: [number, number]) => {
        console.log(`Offset range event: ${JSON.stringify(range)}`)
        let earliest = range[0]
        let latest = range[1]
        if(min_offset.value && (min_offset.value - 20) > earliest ) {
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": min_offset.value-1, "earliest": min_offset.value - 20})
        } else if(min_offset.value && (min_offset.value - 20) <= earliest) {
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": min_offset.value-1, "earliest": earliest})
        } else if((latest - earliest) > 20) {
            // we have more than 20 events to read
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest, "earliest": latest - 19})
        } else {
            // we have less than 20 so just read all of them
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest, "earliest": earliest})
        }
    })

    console.log("Emitting test_event")
    socket.value.emit("test_event", "test data")
    
})

onUnmounted(() => {
    if(socket.value) {
        console.log("Disconnecting from websocket")
        socket.value.disconnect()
    }
        
})
const props = defineProps<Props>()
const emit = defineEmits(["delete"])

let formattedQuery = ref("")

function deleteQuery() {
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query: props.query.query
        })
    }
    
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query/delete`, options)
    .then((res) => res.json())
    .then((data) => {
        if(data["ok"] == 1) {
            // delete this component
            console.log("Deleting QUERY")
            emit("delete")
        } else {
            console.log("NOT deleting query")
            alert("Could not delete query")
        }
    })
    .catch((err) => {
        console.log(err)
        alert("Error occurred while attempting to delete query")
    }) 
}

function readStream() {
    // socket.value?.emit("read_stream", {"query_id": props.query.queryId}, 10)
    console.log("===== checking range ======")
    socket.value?.emit("stream_range", {"query_id": props.query.queryId})
}

function onScroll(event: UIEvent) {
    console.log(`Running onScroll function - ${JSON.stringify(event)}`)
    // offsetHeight is the height of the div
    // scrollTop is the distance between the top of the div and the beginning of the content 
    // (even if scrolled above the start of the div) 
    // So offsetHeight + scrollTop should be the entire length of the div content when the 
    // scrollable content is scrolled to the bottom.
    let sum = (event.target as HTMLElement).offsetHeight + (event.target as HTMLElement).scrollTop

    // scrollHeight - the entire length of the scrollable content
    let comparison = (event.target as Element).scrollHeight
    if(sum >= comparison) {
        console.log("scrolled to bottom")
        // now we load additional data
        // TODO: Need to adjust the call for more data 
        // Should be able to accept parameter of where to start (ie start sending the data after the most recent we have)
        socket.value?.emit("stream_range", {"query_id": props.query.queryId})
    }
}

</script>

<template>
    <div>
        <div class="border border-primary p-2">
            Query ID: {{ props.query.queryId }}            
        </div>
        <br>
        <p>Submitted Query:</p>
        <div class="border border-primary p-3 text-dark">
            <p class="m-0 p-0" v-for="line in props.query.query.split('\n')">
                {{line}}
            </p>
        </div>
        <br>
        <button @click="deleteQuery()" class="btn btn-danger">
            Delete Query
            <font-awesome-icon icon="trash" />
        </button>
    </div>
    <!-- TODO: Add display to show live updating panel of query metrics -->
    <!-- TODO: Add display to show live updating panel of query matches -->
    <button @click="readStream()" class="btn btn-info">Get Recent Events</button>
    <p>Showing from {{min_offset}} to {{max_offset}} </p>
    <div class="my-2 match-list" @scroll="onScroll">
        <p v-for="match in match_list" class="match-item">
            something
            {{match}}
        </p>
    </div>
</template>

<style>
p {
    min-height: 12px;
}

.match-list {
    max-height: 50vh; 
    overflow: scroll;
}

.match-item:hover {
    background-color: rgb(217, 252, 252);
}

</style>