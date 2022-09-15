<script setup lang="ts">

import {ref, onMounted, onUnmounted} from 'vue'
import type {Ref} from 'vue'
import type {QueryI, CEPMatch, EventTypes} from '../stores/query'
// import {queryResultStore} from '../stores/queryResult'
import {backendIp} from '../config'
import { io, Socket} from "socket.io-client"
import type { DefaultEventsMap, EventNames, EventParams, EventsMap, Emitter } from "@socket.io/component-emitter";
import { logger } from "../utils/logging"
import AlertBanner from "./AlertBanner.vue"
import { propsToAttrMap } from '@vue/shared'
import EventTypeInput from './EventTypeInput.vue'

interface Props {
    query: QueryI
}

interface ServerToClient {
    noArg: () => void
    cep_match: (a: CEPMatch) => any
    test_event: (a: any) => any
    offset_range: (a: [number, number]) => any
    error: (msg: string) => any
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

const props = defineProps<Props>()
const emit = defineEmits(["delete"])

let socket: Ref<Socket<ServerToClient, ClientToServer> | null> = ref(null)
let min_visible = ref(0)
let max_visible = ref(0)
let formattedQuery = ref("")
let error_msg: Ref<string | undefined> = ref("")
let earliest = ref(0)
let latest = ref(0)
let show_fields = ref(false)
let show_timestamps = ref(true)
let show_query = ref(true)
let query_event_types = ref([] as EventTypes[])
let event_inputs = ref(new Map<string, Object>())
const event_input_forms = ref([] as string[])

onMounted(() => {
    logger.debug("Query view panel mounted")
    if(socket.value == null ) {
        socket.value = io("ws://localhost:5000/")
    } else if( ! socket.value.connected) {
        socket.value.connect()
    }


    socket.value.on("cep_match", (match: CEPMatch) => {
        props.query.add(match)        
        updateMatchList()
    })

    socket.value.on("offset_range", (range: [number, number]) => {
        earliest.value = range[0]
        latest.value = range[1]
        // logger.info(`Updated earliest and latest offsets: [${range[0]}, ${range[1]}]`)
    })

    socket.value.on("error", (error: string) => {
        error_msg.value = error
        logger.debug(`Setting error message: ${error}`)
    })

    socket.value?.emit("stream_range", {"query_id": props.query.queryId})
    updateMatchList()
    getPlan()
})

onUnmounted(() => {
    if(socket.value) {
        logger.debug("Disconnecting from websocket")
        socket.value.disconnect()
    }
        
})



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
            logger.debug("Deleting QUERY")
            emit("delete")
        } else {
            logger.debug("NOT deleting query")
            alert("Could not delete query")
        }
    })
    .catch((err) => {
        logger.error(err)
        alert("Error occurred while attempting to delete query")
    }) 
}

function handleAST(ast: any) {
    let event_types = ast.event_types
    let parsed_ets = []
    for(var i = 0; i < event_types.length; i++) {
        let name = event_types[i].name
        let fields = event_types[i].fields
        let et: EventTypes = {
            event_name: name,
            event_fields: fields
        }
        parsed_ets.push(et)
    }
    console.log(parsed_ets)
    query_event_types.value = parsed_ets
} 

function getPlan() {
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query_id: props.query.queryId
        })
    }
    console.log(props.query.queryId)
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query/ast`, options)
    // .then((res) => res.json())
    .then((res) => res.json() )
    .then((data) => {
        handleAST(data["ast"])
    })
}

function readStream() {
    // socket.value?.emit("read_stream", {"query_id": props.query.queryId}, 10)
    if(min_visible.value == 0 && max_visible.value == 0) {
        getLatest()
    } else if(props.query.results.length === 0) {
        getLatest()
    }
}

function getLatest() {
    if(max_visible.value === 0) {
        socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": latest.value - 19})
    } else {
        logger.debug("Called getLatest() when some values were already populated")
    }
}

function getLater() {

    if(max_visible.value !== min_visible.value) {
        if((max_visible.value + 20) < latest.value ) {
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": max_visible.value + 20, "earliest": max_visible.value + 1})
        } else  { 
            // )max_visible.value + 20) >= latest.value
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": max_visible.value + 1})
        }
    } else {
        if((latest.value - earliest.value) > 20) {
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": latest.value - 19})
        } else {
            // we have less than 20 so just read all of them
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": earliest.value})
        }
    }
     
}

function getEarlier() {
    if(min_visible.value !== max_visible.value) {
        if((min_visible.value - 20) > earliest.value ) {
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": min_visible.value-1, "earliest": min_visible.value - 20})
        } else {
            // (min_visible.value - 20) <= earliest.value)
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": min_visible.value-1, "earliest": earliest.value})
        }
    } else {
        if((latest.value - earliest.value) > 20) {
            // we have more than 20 events to read
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": latest.value - 19})
        } else {
            // we have less than 20 so just read all of them
            socket.value?.emit("read_stream", {"query_id": props.query.queryId, "latest": latest.value, "earliest": earliest.value})
        }
    }
}

function onScroll(event: UIEvent) {
    
    // console.log(`offsetHeight: ${(event.target as HTMLElement).offsetHeight} - scrollTop: ${(event.target as HTMLElement).scrollTop}`)
    if((event.target as HTMLElement).scrollTop === 0) {
        // scrolled to the top
        getLater()
    } else {
        // Scrolled to the bottom
        // offsetHeight is the height of the div
        // scrollTop is the distance between the top of the div and the beginning of the content 
        // (even if scrolled above the start of the div) 
        // So offsetHeight + scrollTop should be the entire length of the div content when the 
        // scrollable content is scrolled to the bottom.
    
        let sum = (event.target as HTMLElement).offsetHeight + (event.target as HTMLElement).scrollTop

        // scrollHeight - the entire length of the scrollable content
        let comparison = (event.target as Element).scrollHeight
        if(sum >= comparison) {
            // now we load additional data
            // TODO: Need to adjust the call for more data 
            // Should be able to accept parameter of where to start (ie start sending the data after the most recent we have)
            // socket.value?.emit("stream_range", {"query_id": props.query.queryId})
            getEarlier()
        }
    }
}

function updateMatchList() {
    
    let visible_offsets = props.query.results.map((match) => match.offset)
    if(visible_offsets.length > 0) {
        min_visible.value = Math.min.apply(null, visible_offsets);
        max_visible.value = Math.max.apply(null, visible_offsets);
    } else {
        min_visible.value = 0
        max_visible.value = 0
    }
    
}

function clearError() {
    error_msg.value = ""
}

function dateTimeFormat(epoch: number): string {
    let d = new Date(epoch * 1000)
    let month = d.getMonth()
    let date = d.getDate()
    let year = d.getFullYear()
    let hour = d.getHours()
    let minute = d.getMinutes()
    let now = new Date()
    // @ts-ignore
    let formatter = new Intl.DateTimeFormat("en-US", {dateStyle: "medium", timeStyle: "medium"})
    return formatter.format(d)
}

</script>

<template>
    <div>
        <div class="border border-primary p-2">
            Query ID: {{ props.query.queryId }}            
        </div>
        <br>
        <div v-if="show_query" class="my-2">
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
    </div>
    
    <!-- TODO: Add display to show live updating panel of query metrics -->
    <!-- TODO: Add display to show live updating panel of query matches -->
    <button @click="readStream()" class="btn btn-info">Get Recent Events</button>
    <button @click="show_fields = !show_fields" class="mx-2 btn btn-secondary">Toggle Field Names</button>
    <button @click="show_timestamps = !show_timestamps" class="mx-2 btn btn-secondary">Toggle Timestamps</button>
    <button @click="show_query = !show_query" class="mx-2 btn btn-secondary">Toggle Query Display</button>
    <!-- <button @click="getPlan()" class="mx-2 btn btn-secondary">Print Plan</button> -->
    <div>
        <label>WHERE filter:</label>
        <input class="m-2 form form-control" type="text" placeholder="a.field1 < 1000 AND ...">
    </div>
    
    <div>
        <label>Submit Event:</label>
        <EventTypeInput :event_types="query_event_types" :query_id="props.query.queryId"></EventTypeInput>
    </div>


    <p>Showing from {{min_visible}} to {{max_visible}} </p>
    <div class="my-2 match-list" @scroll="onScroll">
        <div v-for="match in props.query.results" class="match-item">
            <span class="px-2">{{match["offset"]}}</span>
            <span v-if="show_timestamps" class="px-2">{{dateTimeFormat(match["match"]["Composite"]["timestamp"])}}</span>
            <!-- <span class="px-2">{{match["match"]["Composite"]["events"]}}</span> -->
            <span class="px-2"> 
                <span class="px-2" v-for="event in match.match.Composite.events">
                {{event.event_type}} {{event.bind_name}} ( <span class="" v-for="(field, index) in event.fields"> <span class="text-primary fw-light" v-if="show_fields"> {{ field.field_name + ": "}}</span> <span class=""> {{index !== event.fields.length-1 ? field.field_value + ", " : field.field_value }} </span> </span> )  
                </span>
            </span>
        </div>
    </div>
    <AlertBanner v-if="error_msg" :msg="error_msg" alert_type="danger" :close_fn="clearError"></AlertBanner>
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

.event-type-inputs {
    width: 10%;
    display: inline;
}

</style>