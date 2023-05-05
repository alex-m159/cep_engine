<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import type { Ref } from "vue";
import type { QueryI, CEPMatch, EventTypes, QueryStoreT } from "../stores/query";
// import {queryResultStore} from '../stores/queryResult'

import useQueryStore from "../stores/query";
import { io, Socket } from "socket.io-client";
import type {
  DefaultEventsMap,
  EventNames,
  EventParams,
  EventsMap,
  Emitter,
} from "@socket.io/component-emitter";
import { logger } from "../utils/logging";
import AlertBanner from "./AlertBanner.vue";
import { propsToAttrMap } from "@vue/shared";
import EventTypeInput from "./EventTypeInput.vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import type { editor } from "monaco-editor";
import { createEditor } from "../editor/MonacoEditor";
import { call } from "../utils/networking";

interface Props {
  queryStore: QueryStoreT;
}

interface ServerToClient {
  noArg: () => void;
  cep_match: (a: CEPMatch) => any;
  test_event: (a: any) => any;
  offset_range: (a: [number, number]) => any;
  error: (msg: string) => any;
}

interface ClientToServer {
  hello: () => void;
  test_event: (data: string) => void;
  read_stream: (qid: any) => any;
  stream_range: (qid: any) => any;
}

interface InterServerEvents {
  ping: () => void;
}

interface SocketData {
  something: string;
  else: number;
}

const queryStore = useQueryStore();
const emit = defineEmits(["delete"]);
const query: Ref<QueryI | undefined> = ref(undefined);

let socket: Ref<Socket<ServerToClient, ClientToServer> | null> = ref(null);
let min_visible = ref(0);
let max_visible = ref(0);
let formattedQuery = ref("");
let error_msg: Ref<string | undefined> = ref("");
let earliest = ref(0);
let latest = ref(0);
let show_fields = ref(false);
let show_timestamps = ref(true);
let show_query = ref(true);
let query_event_types = ref([] as EventTypes[]);
let event_inputs = ref(new Map<string, Object>());
const show_parsed = ref(false);
const show_plan = ref(false);
const query_ast = ref({});
const query_plan = ref({});
const event_input_forms = ref([] as string[]);
const router = useRouter();
const route = useRoute();
const monaco_container: Ref<HTMLElement> = ref((null as unknown) as HTMLElement);
var query_editor: editor.IStandaloneCodeEditor = (null as unknown) as editor.IStandaloneCodeEditor;


onMounted(() => {
  logger.debug("Query view panel mounted");
  if (socket.value == null) {
    socket.value = io("ws://localhost:5000/");
  } else if (!socket.value.connected) {
    socket.value.connect();
  }

  router
    .isReady()
    .then(() => {
      let query_id: number = Number(route.params.query_id);
      query.value = queryStore.queriesAsMap.get(query_id);
      query_editor = createEditor(monaco_container.value, query.value?.query, true);
    })
    .then(() => {
      updateMatchList();
      if (query.value !== undefined) {
        getParsed();
        getPlan();
      }
      socket.value?.emit("stream_range", { query_id: query.value?.queryId });
    });

  socket.value.on("cep_match", (match: CEPMatch) => {
    query.value?.add(match);
    updateMatchList();
  });

  socket.value.on("offset_range", (range: [number, number]) => {
    earliest.value = range[0];
    latest.value = range[1];
  });

  socket.value.on("error", (error: string) => {
    error_msg.value = error;
    logger.debug(`Setting error message: ${error}`);
  });
});

onUnmounted(() => {
  if (socket.value) {
    logger.debug("Disconnecting from websocket");
    socket.value.disconnect();
  }
});

function deleteQuery() {
  call("/query/delete", "POST", {
    query: query.value?.query,
  })
    .then((res: Response) => res.json())
    .then((data) => {
      if (data["ok"] == 1) {
        // delete this component
        emit("delete");
      } else {
        alert("Could not delete query");
      }
    })
    .catch((err) => {
      logger.error(err);
      alert("Error occurred while attempting to delete query");
    });
}

function handleAST(ast: any) {
  let event_types = ast.event_types;
  let parsed_ets = [];
  for (var i = 0; i < event_types.length; i++) {
    let name = event_types[i].name;
    let fields = event_types[i].fields;
    let et: EventTypes = {
      event_name: name,
      event_fields: fields,
    };
    parsed_ets.push(et);
  }
  query_event_types.value = parsed_ets;
}

function getParsed() {
  call("/query/ast", "POST", {
    query_id: query.value?.queryId,
  })
    .then((res) => res.json())
    .then((data) => {
      if (data["ok"] === 1) {
        query_ast.value = data["ast"];
        return data["ast"];
      } else {
        throw Error("No AST returned from backend");
      }
    });
}

function getPlan(): Promise<string> {
  return call("/query/plan", "POST", {
    query: query.value?.query,
  })
    .then((res) => res.json())
    .then((data) => {
      if (data["ok"] === 1) {
        query_plan.value = data["plan"];
        return data["plan"];
      } else {
        throw Error("No plan returned from backend");
      }
    });
}

function readStream() {
  // socket.value?.emit("read_stream", {"query_id": query.queryId}, 10)
  if (min_visible.value == 0 && max_visible.value == 0) {
    getLatest();
  } else if (query.value?.results.length === 0) {
    getLatest();
  }
}

function getLatest() {
  if (max_visible.value === 0) {
    socket.value?.emit("read_stream", {
      query_id: query.value?.queryId,
      latest: latest.value,
      earliest: latest.value - 19,
    });
  } else {
    logger.debug("Called getLatest() when some values were already populated");
  }
}

function getLater() {
  if (max_visible.value !== min_visible.value) {
    if (max_visible.value + 20 < latest.value) {
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: max_visible.value + 20,
        earliest: max_visible.value + 1,
      });
    } else {
      // )max_visible.value + 20) >= latest.value
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: latest.value,
        earliest: max_visible.value + 1,
      });
    }
  } else {
    if (latest.value - earliest.value > 20) {
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: latest.value,
        earliest: latest.value - 19,
      });
    } else {
      // we have less than 20 so just read all of them
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: latest.value,
        earliest: earliest.value,
      });
    }
  }
}

function getEarlier() {
  if (min_visible.value !== max_visible.value) {
    if (min_visible.value - 20 > earliest.value) {
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: min_visible.value - 1,
        earliest: min_visible.value - 20,
      });
    } else {
      // (min_visible.value - 20) <= earliest.value)
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: min_visible.value - 1,
        earliest: earliest.value,
      });
    }
  } else {
    if (latest.value - earliest.value > 20) {
      // we have more than 20 events to read
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: latest.value,
        earliest: latest.value - 19,
      });
    } else {
      // we have less than 20 so just read all of them
      socket.value?.emit("read_stream", {
        query_id: query.value?.queryId,
        latest: latest.value,
        earliest: earliest.value,
      });
    }
  }
}

function onScroll(event: UIEvent) {
  if ((event.target as HTMLElement).scrollTop === 0) {
    // scrolled to the top
    getLater();
  } else {
    // Scrolled to the bottom
    // offsetHeight is the height of the div
    // scrollTop is the distance between the top of the div and the beginning of the content
    // (even if scrolled above the start of the div)
    // So offsetHeight + scrollTop should be the entire length of the div content when the
    // scrollable content is scrolled to the bottom.

    let sum =
      (event.target as HTMLElement).offsetHeight +
      (event.target as HTMLElement).scrollTop;

    // scrollHeight - the entire length of the scrollable content
    let comparison = (event.target as Element).scrollHeight;
    if (sum >= comparison) {
      // now we load additional data
      // TODO: Need to adjust the call for more data
      // Should be able to accept parameter of where to start (ie start sending the data after the most recent we have)
      // socket.value?.emit("stream_range", {"query_id": query.queryId})
      getEarlier();
    }
  }
}

function updateMatchList() {
  let visible_offsets = query.value?.results.map((match) => match.offset);
  if (visible_offsets && visible_offsets.length > 0) {
    min_visible.value = Math.min.apply(null, visible_offsets);
    max_visible.value = Math.max.apply(null, visible_offsets);
  } else {
    min_visible.value = 0;
    max_visible.value = 0;
  }
}

function clearError() {
  error_msg.value = "";
}

function dateTimeFormat(epoch: number): string {
  let d = new Date(epoch * 1000);
  let month = d.getMonth();
  let date = d.getDate();
  let year = d.getFullYear();
  let hour = d.getHours();
  let minute = d.getMinutes();
  let now = new Date();
  // @ts-ignore
  let formatter = new Intl.DateTimeFormat("en-US", {
    // @ts-ignore
    dateStyle: "medium",
    // @ts-ignore
    timeStyle: "medium",
  });
  return formatter.format(d);
}

function togglePlan() {
  if (query_plan.value === undefined) {
    getPlan().then(
      (_) => {
        show_plan.value = !show_plan.value;
      },
      (_) => {
        show_plan.value = !show_plan.value;
      }
    );
  } else {
    show_plan.value = !show_plan.value;
  }
}

function toggleParsed() {
  if (query_ast.value === undefined) {
    getPlan().then(
      (_) => {
        show_parsed.value = !show_parsed.value;
      },
      (_) => {
        show_parsed.value = !show_parsed.value;
      }
    );
  } else {
    show_parsed.value = !show_parsed.value;
  }
}
</script>

<template>
  <div class="row">
    <!-- Query Title -->
    <div class="col">
      <h4 class="p-2">Query ID: {{ query?.queryId }}</h4>
    </div>
  </div>

  <div class="row">
    <div class="col">
      <ul class="nav nav-tabs">
        <li class="nav-item">
          <a class="nav-link active" aria-current="page" href="#">Query</a>
        </li>
        <li class="nav-item">
          <a class="nav-link">Event Matches</a>
        </li>
        <li class="nav-item">
          <a class="nav-link" href="#">Metrics</a>
        </li>
      </ul>
    </div>
  </div>
  <div class="row">
    <div class="col">
      <div :class="{ 'd-none': !show_query }" class="my-2">
        <p>Submitted Query:</p>
        <div id="container" ref="monaco_container" style="height: 30vh"></div>
      </div>
    </div>
  </div>
  <div class="row">
    <div class="col">
      <button @click="readStream()" class="mx-2 btn btn-outline-dark">
        <i class="bi bi-clock-history"></i>
        Get Recent Events
      </button>
      <button @click="show_fields = !show_fields" class="mx-2 btn btn-outline-dark">
        <i
          class="bi"
          :class="{ 'bi-eye': show_fields, 'bi-eye-slash': !show_fields }"
        ></i>
        Toggle Field Names
      </button>
      <button
        @click="show_timestamps = !show_timestamps"
        class="mx-2 btn btn-outline-dark"
      >
        <i
          class="bi"
          :class="{ 'bi-eye': show_timestamps, 'bi-eye-slash': !show_timestamps }"
        ></i>
        Toggle Timestamps
      </button>
      <button @click="show_query = !show_query" class="mx-2 btn btn-outline-dark">
        <i class="bi" :class="{ 'bi-eye': show_query, 'bi-eye-slash': !show_query }"></i>
        Toggle Query Display
      </button>
      <button @click="toggleParsed()" class="mx-2 btn btn-outline-dark">
        <i class="bi bi-map"></i>
        Show Parsed
      </button>
      <button @click="togglePlan()" class="mx-2 btn btn-outline-dark">
        <i class="bi bi-map"></i>
        Show Plan
      </button>
      <button @click="deleteQuery()" class="mx-2 btn btn-outline-danger">
        <i class="bi bi-trash-fill" style="font-size: 1em"></i>
        Delete Query
      </button>
    </div>
  </div>

  <!-- TODO: Add display to show live updating panel of query metrics -->
  <!-- TODO: Add display to show live updating panel of query matches -->

  <div class="row">
    <div class="col">
      <label class="d-inline-flex">WHERE filter:</label>
      <input
        class="m-2 form form-control"
        type="text"
        placeholder="a.field1 < 1000 AND ..."
      />
    </div>
  </div>

  <div class="row">
    <div class="col">
      <label>Submit events to query:</label>
      <EventTypeInput
        v-if="query?.queryId"
        :event_types="query_event_types"
        :query_id="query?.queryId"
      ></EventTypeInput>
    </div>
  </div>

  <div v-if="show_parsed" class="row">
    <div class="col">
      <p>
        {{ JSON.stringify(query_ast, null, 2) }}
      </p>
    </div>
  </div>

  <div v-if="show_plan" class="row">
    <div class="col">
      <p>
        {{ JSON.stringify(query_plan, null, 2) }}
      </p>
    </div>
  </div>

  <div class="row">
    <div class="col">
      <p>Showing from {{ min_visible }} to {{ max_visible }}</p>
    </div>
  </div>

  <div class="row">
    <div class="col">
      <div class="my-2 match-list" @scroll="onScroll">
        <div v-for="match in query?.results" class="match-item">
          <span class="px-2">{{ match["offset"] }}</span>
          <span v-if="show_timestamps" class="px-2">{{
            dateTimeFormat(match["match"]["Composite"]["timestamp"])
          }}</span>
          <!-- <span class="px-2">{{match["match"]["Composite"]["events"]}}</span> -->
          <span class="px-2">
            <span class="px-2" v-for="event in match.match.Composite.events">
              {{ event.event_type }} {{ event.bind_name }} (
              <span class="" v-for="(field, index) in event.fields">
                <span class="text-primary fw-light" v-if="show_fields">
                  {{ field.field_name + ": " }}</span
                >
                <span class="">
                  {{
                    index !== event.fields.length - 1
                      ? field.field_value + ", "
                      : field.field_value
                  }}
                </span>
              </span>
              )
            </span>
          </span>
        </div>
      </div>
    </div>
  </div>
  <div class="row">
    <div class="col">
      <AlertBanner
        v-if="error_msg"
        :msg="error_msg"
        alert_type="danger"
        :close_fn="clearError"
      ></AlertBanner>
    </div>
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

.event-type-inputs {
  width: 10%;
  display: inline;
}
</style>
