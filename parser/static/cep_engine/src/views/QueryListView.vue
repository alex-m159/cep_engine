<script setup lang="ts">
// import QueryListItem from "../components/QueryListItem.vue"
import QueryListItem from '@/components/QueryListItem.vue'
import QueryEditor from '@/components/QueryEditor.vue';
import {onMounted, onUnmounted, ref, type Ref} from 'vue'
import type {Query, QueryStoreT} from "../stores/query"
import type {ActiveQueryStoreT} from '../stores/activeQuery'
import {pinia} from "../stores/query"
import QueryStore from '../stores/query'
import ActiveQueryStore from '../stores/activeQuery'
import QueryListCenterList from '../components/QueryListCenterList.vue'
import QueryVerticalSideList from '../components/QueryVerticalSideList.vue'
import {backendIp} from '../config'
import type { QueryResultStoreT } from '@/stores/queryResult';
import { QueryResultStore } from '@/stores/queryResult';

interface Props {
  queryStore: QueryStoreT
  activeQueryStore: ActiveQueryStoreT
  queryResultStore: QueryResultStoreT
}

const props = defineProps<Props>()


// console.log("Props")
// console.log(props.queryStore)
// let allQueries: Ref<Query[]> = ref(Array.from(props.queryStore.queries.values()))

let someVal = ref("something")
let count = 0

// function queriesFromStore(): Query[] {
//     return Array.from(props.queryStore.queries.values())
// }



onMounted(() => {
    console.log("The QueryListView component is mounted")
    let options = {
        mode: 'GET',
    }
    fetch(`http://${backendIp}/query`)
    .then((r) => {

        return r.json()
    })
    .then((data) => {
        let newData: Query[] = data.queries.map((item: any) => {return {queryId: item.query_id, query: item.query_string}})

        newData.forEach((q) => {
            props.queryStore.upsertQuery(q)
        })
        console.log("Result from backend")
        console.log(data)
    })
    .catch((err) => {
        console.log("Error from backend")
        console.log(err)
    })
})

onUnmounted(() => {
    console.log("The QueryListView is UNmounted")
})

let showingEditor = ref(false)
</script>

<template>
  <main>
    <h2 v-if="props.queryStore.queriesAsList.length == 0">No queries running</h2>

    <div class="m-3">
      <button v-if="!showingEditor" @click="showingEditor = true" class="btn btn-danger">
        New Query
        <font-awesome-icon icon="pen" />
      </button>
      <button v-if="showingEditor" @click="showingEditor = false" class="btn btn-danger">
        Close
        <font-awesome-icon icon="x" />
      </button>
      <div v-if="showingEditor" class="my-2">
        <QueryEditor :query-store="props.queryStore"></QueryEditor>
      </div>
    </div>
    <!-- <QueryListCenterList :query-store="queryStore"></QueryListCenterList> -->
    <QueryVerticalSideList
      :query-store="props.queryStore"
      :active-query-store="props.activeQueryStore"
      :query-result-store="props.queryResultStore"
    ></QueryVerticalSideList>
  </main>
</template>
