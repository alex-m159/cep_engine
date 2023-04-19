<script setup lang="ts">
// import QueryListItem from "../components/QueryListItem.vue"
import QueryListItem from '@/components/QueryListItem.vue'
import QueryEditor from '@/components/QueryEditor.vue';
import {onMounted, onUnmounted, onBeforeMount, ref, type Ref} from 'vue'
import {Query, type QueryI, type QueryStoreT} from "../stores/query"
import {pinia} from "../stores/query"
import useQueryStore from '../stores/query'
import QueryListCenterList from '../components/QueryListCenterList.vue'
import QueryVerticalSideList from '../components/QueryVerticalSideList.vue'
import {backendIp} from '../config'
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
// import Pagination from 'v-pagination-3';


const queryStore = useQueryStore()

const page_size = ref(15)
const queries: Ref<QueryI[]> = ref([])
const router = useRouter()
const route = useRoute()
const total_pages = ref(0)
const current_page = ref(1)

console.log(`PROPS: ${queryStore.queriesAsList.length}`)

function updateQueries(page_number?: number) {
  let qs = queryStore.queriesAsList
  if(qs.length < page_size.value) {
    queries.value = qs
  } else if(page_number === undefined) {
    let page = 1
    let start = (page-1)*page_size.value
    let end = (page*page_size.value)
    queries.value = qs.slice(start, end)
    current_page.value = 1
  } else {
    let page = page_number
    let start = (page-1)*page_size.value
    let end = (page*page_size.value)
    console.log(`Start = ${start}, End = ${end}`)
    queries.value = qs.slice(start, end)
    console.log(`Queries.value = ${queries.value.length}`)
    router.push(`/queries/list/${page_number}`)
    current_page.value = page_number
  }
  
}


onMounted(() => {
    console.log("The QueryListView component is mounted")
    let options = {
        mode: 'GET',
    }
    fetch(`http://${backendIp}/query`)
    .then((r) => {

        return r.json()
    })
    .then((data) => {let newData: QueryI[] = data.queries.map((item: any) => new Query(Number(item.query_id), item.query_string) )
        newData.forEach((q) => {
            queryStore.upsertQuery(q)
        })
        console.log(`PROPS: ${queryStore.queriesAsList.length}`)
        total_pages.value = Math.ceil(queryStore.queriesAsList.length / page_size.value)
        if(route.params.page) {
          //@ts-ignore
          updateQueries(route.params.page)
        } else {
          updateQueries()
        }
    })
    .catch((err) => {
        console.log("Error from backend")
        console.log(err)
    })

})
</script>

<template>
  <div class="row" style="height: 10%">
    <div class="col">
      <RouterLink to="/queries/new">
        <button class="btn btn-outline-dark m-4">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="30"
            height="30"
            fill="currentColor"
            class="bi bi-plus"
            viewBox="0 0 16 16"
          >
            <path
              d="M8 4a.5.5 0 0 1 .5.5v3h3a.5.5 0 0 1 0 1h-3v3a.5.5 0 0 1-1 0v-3h-3a.5.5 0 0 1 0-1h3v-3A.5.5 0 0 1 8 4z"
            />
          </svg>
          Add Query
        </button>
      </RouterLink>
    </div>
  </div>

  <div
    v-if="queryStore.queriesAsList.length == 0"
    class="row flex-fill"
    style="height: 90%"
  >
    <div class="col h-100">
      <h3 class="d-flex h-100 justify-content-center align-items-center">
        No queries running
      </h3>
    </div>
  </div>

  <div class="row" v-if="queryStore.queriesAsList.length > page_size">
    <div class="col">
      <paginate
        v-model="current_page"
        class="justify-content-center mt-auto"
        :page-count="total_pages"
        :prev-text="'Prev'"
        :next-text="'Next'"
        :click-handler="updateQueries"
      >
      </paginate>
    </div>
  </div>
  <div class="row" v-if="queryStore.queriesAsList.length > page_size">
    <table class="table table-hover table-bordered">
      <thead class="table-dark">
        <tr>
          <th scope="col">Query ID</th>
          <th scope="col">Name</th>
          <th scope="col">State</th>
          <th scope="col">Duration</th>
          <th scope="col">Last Output Time</th>
          <th scope="col">Number of Results</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="query in queries">
          <th scope="row">{{ query.queryId }}</th>
          <td>DEV Query <i class="bi bi-pencil float-end"></i></td>
          <td>Active</td>
          <td>5h 8m</td>
          <td>5m ago</td>
          <td>20k matches</td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="row" v-if="queryStore.queriesAsList.length <= page_size">
    <div class="col">
      <table class="table table-hover table-bordered">
        <thead class="table-dark">
          <tr>
            <th scope="col">Query ID</th>
            <th scope="col">Name</th>
            <th scope="col">State</th>
            <th scope="col">Duration</th>
            <th scope="col">Last Output Time</th>
            <th scope="col">Number of Results</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="query in queries">
            <th scope="row"> <RouterLink :to="`/queries/${query.queryId}`"> {{ query.queryId }} </RouterLink> </th>
            <td>DEV Query <i class="bi bi-pencil float-end"></i></td>
            <td>Active</td>
            <td>5h 8m</td>
            <td>5m ago</td>
            <td>20k matches</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
