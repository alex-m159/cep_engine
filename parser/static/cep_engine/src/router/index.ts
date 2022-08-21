import { createRouter, createWebHistory } from 'vue-router'
//@ts-ignore
import HomeView from '../views/HomeView.vue'
// @ts-ignore
import QueryListView from '../views/QueryListView.vue'
import type { QueryResultStoreT } from '@/stores/queryResult';
import { QueryResultStore } from '@/stores/queryResult';
import type {Query, QueryStoreT} from "../stores/query"
import type {ActiveQueryStoreT} from '../stores/activeQuery'
import {pinia} from "../stores/query"
import QueryStore from '../stores/query'
import ActiveQueryStore from '../stores/activeQuery'

const queryStore: QueryStoreT = QueryStore(pinia)
const activeQueryStore: ActiveQueryStoreT = ActiveQueryStore(pinia)
const queryResultStore: QueryResultStoreT = QueryResultStore(pinia)

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      // @ts-ignore
      component: () => import('../views/AboutView.vue')
    },
    {
      path: '/queries',
      name: 'queries',
      component: QueryListView,
      props: {
        queryStore: queryStore,
        activeQueryStore: activeQueryStore,
        queryResultStore: queryResultStore
      }
    }
  ]
})


export default router
