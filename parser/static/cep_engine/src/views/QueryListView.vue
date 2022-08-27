<script setup lang="ts">
// import QueryListItem from "../components/QueryListItem.vue"
import QueryListItem from '@/components/QueryListItem.vue'
import QueryEditor from '@/components/QueryEditor.vue';
import {onMounted, onUnmounted, ref, type Ref} from 'vue'
import {Query, type QueryI, type QueryStoreT} from "../stores/query"
import {pinia} from "../stores/query"
import QueryStore from '../stores/query'
import QueryListCenterList from '../components/QueryListCenterList.vue'
import QueryVerticalSideList from '../components/QueryVerticalSideList.vue'
import {backendIp} from '../config'


interface Props {
  queryStore: QueryStoreT
}

const props = defineProps<Props>()



let someVal = ref("something")
let count = 0


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
        let newData: QueryI[] = data.queries.map((item: any) => new Query(item.query_id, item.query_string) )

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
    ></QueryVerticalSideList>
  </main>
</template>
