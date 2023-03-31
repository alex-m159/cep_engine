<script setup lang="ts">

import type {QueryI, QueryStoreT} from '../stores/query'
import QueryListItem from './QueryListItem.vue'
import {ref, type Ref} from 'vue'

interface Props {
    queryStore: QueryStoreT
}

const props = defineProps<Props>()

function deleteItem(queryId: number) {
    console.log("Deleting query from Pinia Store")
    console.log(queryId)
    props.queryStore.deleteQuery(queryId)
}


function setActive(qid: number) {
    props.queryStore.setActive(qid)
}

function activeQueryId(): number | undefined {
  return props.queryStore.active?.queryId
}

function activeQuery(): QueryI | undefined {
  return props.queryStore.active
}

let listHidden = ref(true)

</script>
<template>
  
  <div class="row">
    <div class="navbar navbar-light navbar-expand-md col-md-2 d-block p-0">
      <div class="container px-0">
        <a class="navbar-brand" href="#"> </a>
        <button
          @click="listHidden = !listHidden"
          class="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarSupportedContent3"
          aria-controls="navbarSupportedContent3"
          aria-expanded="false"
          aria-label="Query navigation"
        >
          <!-- <span class="fa-angle-down"></span> -->
          <font-awesome-icon v-if="listHidden" icon="angle-down" />
          <font-awesome-icon v-if="!listHidden" icon="angle-up" />
        </button>

        <div class="navbar-collapse collapse" id="navbarSupportedContent3">
          <ul class="nav nav-pills flex-column navbar-nav">
            <li v-for="query in props.queryStore.queriesAsList" class="nav-item">
              <p
                @click="setActive(query.queryId)"
                v-bind:class="{ active: activeQueryId() == query.queryId }"
                :qid="query.queryId"
                class="nav-link text-dark"
                aria-current="page"
                href="#"
              >
                Query: {{ query.queryId }}
              </p>
            </li>
          </ul>
        </div>
      </div>
    </div>
    <div class="col-md-10">
      <div v-for="query in props.queryStore.queriesAsList">
        <QueryListItem
          v-if="props.queryStore.active?.queryId === query.queryId"
          :query="query"
          @delete="props.queryStore.deleteQuery(query.queryId)"
        ></QueryListItem>
      </div>
      <!-- <div >
        <QueryListItem
          v-if="props.queryStore.active !== undefined"
          :query="props.queryStore.active"
          @delete="props.queryStore.deleteQuery(query.queryId)"
        ></QueryListItem>
      </div> -->
      <h2 v-if="props.queryStore.active === undefined">Select a query from the dropdown</h2>

      <!-- <h2 v-if="activeQuery === null || activeQuery === undefined">Select a query from the dropdown</h2>
            <div v-for="query in props.queryStore.queriesAsList">
                <QueryListItem :query="query"></QueryListItem>
            </div> -->
    </div>
  </div>
</template>
