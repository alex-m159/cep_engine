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
  <!-- <div class="row">
        <nav class="navbar navbar-light navbar-expand-md bg-secondary col-md-2">
            <div class="container">
                <a class="navbar-brand" href="#">
                
                </a>
                <button
                class="navbar-toggler"
                type="button"
                data-bs-toggle="collapse"
                data-bs-target="#navbarSupportedContent2"
                aria-controls="navbarSupportedContent"
                aria-expanded="false"
                aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
                </button>

                <div class="collapse navbar-collapse" id="navbarSupportedContent2">
                    <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                        <li class="nav-item">
                        <a class="nav-link active" aria-current="page" href="#">Home</a>
                        <RouterLink to="/" class="nav-link active text-dark">Home</RouterLink>
                        </li>
                        <li class="nav-item">
                        <a class="nav-link" href="#">Link</a>
                        <RouterLink to="/about" class="nav-link active text-dark"
                            >About</RouterLink
                        >
                        </li>
                        <li class="nav-item">
                        <RouterLink to="/queries" class="nav-link active text-dark"
                            >Queries</RouterLink
                        >
                        </li>
                    </ul>
                </div>
            </div>
        </nav>
    </div> -->
  <div class="row">
    <nav class="navbar navbar-light navbar-expand-md col-md-2 d-block">
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
    </nav>
    <div class="col-md-10">
      <div v-for="query in props.queryStore.queriesAsList">
        <QueryListItem
          v-if="props.queryStore.active?.queryId === query.queryId"
          :query="query"
          @delete="props.queryStore.deleteQuery(query.queryId)"
        ></QueryListItem>
      </div>
      <h2 v-if="props.queryStore.active === undefined">Select a query from the dropdown</h2>

      <!-- <h2 v-if="activeQuery === null || activeQuery === undefined">Select a query from the dropdown</h2>
            <div v-for="query in props.queryStore.queriesAsList">
                <QueryListItem :query="query"></QueryListItem>
            </div> -->
    </div>
  </div>
</template>
