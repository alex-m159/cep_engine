<script setup lang="ts">
// import QueryListItem from "../components/QueryListItem.vue"
import QueryListItem from '@/components/QueryListItem.vue'
import QueryEditor from '@/components/QueryEditor.vue';
import {onMounted, onUnmounted, ref, type Ref} from 'vue'
import type {Query, QueryStoreT} from "../stores/query"
import {pinia} from "../stores/query"
import QueryStore from '../stores/query'
import QueryList from '../components/QueryList.vue'

// const props = defineProps<QueryStoreProp>()
const queryStore: QueryStoreT = QueryStore(pinia)


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
    fetch("http://localhost:5000/query")
    .then((r) => {

        return r.json()
    })
    .then((data) => {
        let newData: Query[] = data.queries.map((item: any) => {return {queryId: item.query_id, query: item.query_string}})

        newData.forEach((q) => {
            queryStore.upsertQuery(q)
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
    <h2 v-if="queryStore.queriesAsList.length == 0">No queries running</h2>

    <div class="m-3">
        <button 
         v-if="!showingEditor"
         @click="showingEditor = true"
         class="btn btn-danger">
            New Query
            <font-awesome-icon icon="pen" />
        </button>
        <button 
         v-if="showingEditor"
         @click="showingEditor = false"
         class="btn btn-danger">
            Close
            <font-awesome-icon icon="x" />
        </button>
        <div v-if="showingEditor" class="my-2">
            <QueryEditor :query-store="queryStore"></QueryEditor>
        </div>
    </div>
    <QueryList :query-store="queryStore"></QueryList>
  </main>
</template>
