import { reactive } from 'vue';
import { createRouter, createWebHistory } from 'vue-router'
//@ts-ignore
import HomeView from '../views/HomeView.vue'
// @ts-ignore
import QueryListView from '../views/QueryListView.vue'
import type {QueryI, QueryStoreT} from "../stores/query"
import {pinia} from "../stores/query"
import QueryStore from '../stores/query'


const queryStore: QueryStoreT = QueryStore(pinia)

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
      }
    }
  ]
})


export default router
