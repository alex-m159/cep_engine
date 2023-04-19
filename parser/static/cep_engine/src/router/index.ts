import { reactive } from 'vue';
import { createRouter, createWebHistory } from 'vue-router'
//@ts-ignore
import HomeView from '../views/HomeView.vue'
// @ts-ignore
import QueryListView from '../views/QueryListView.vue'
import type {QueryI, QueryStoreT} from "../stores/query"
import {pinia} from "../stores/query"
import useQueryStore from '../stores/query'
import QueryEditor from '@/components/QueryEditor.vue';
import QueryPage from '@/components/QueryPage.vue';



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
      path: '/queries/list/:page?',
      name: 'queries',
      component: QueryListView,
    },
    {
      path: '/queries/new',
      name: 'new_query',
      component: QueryEditor,
    },
    {
      path: '/queries/:query_id',
      name: 'query_page',
      component: QueryPage,
    }
  ]
})

export default router
