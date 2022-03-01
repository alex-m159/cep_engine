import { defineStore, createPinia } from "pinia"
import type { Store } from "pinia"
import { reactive } from "vue"

export const pinia = createPinia()

export interface Query {
    queryId: number
    query: string
}

// export type QueryStoreT = Store<"queries", {
//     queries: Query[];
// }, {
//     queries: (state: {
//         queries: {
//             queryId: number;
//             query: string;
//         }[];
//     } & {}) => {
//         queryId: number;
//         query: string;
//     }[];
// }, {
//     upsertQuery(query: Query): void;
// }>



let QueryStore = defineStore('query_store', {
    state: () => ({ 
        queryList: new Map<number, Query>()
    }),
    getters: {
        queriesAsMap(state) {
            return state.queryList
        },
        queriesAsList(state) {
            return Array.from(state.queryList.values())
        }
    },
    actions: {
        upsertQuery(query: Query) {
            this.queryList.set(query.queryId, query)
        },
        deleteQuery(queryId: number) {
            return this.queryList.delete(queryId)
        }
    }

})

// Create an instance so that we can get the type of
// Store instead of StoreDefinition.
// We'll only use this for getting the type so we 
// don't have to constantly update it
let inst = QueryStore(pinia)
export type QueryStoreT = typeof inst

export default QueryStore