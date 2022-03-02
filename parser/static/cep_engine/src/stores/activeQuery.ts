import { defineStore, createPinia } from "pinia"
import { pinia } from "./query"

let ActiveQueryStore = defineStore('active_query', {
    state: () => ({ 
        activeQueryState: null as number | null
    }),
    getters: {
        current(state) {
            return state.activeQueryState
        }
    },
    actions: {
        set(qid: number) {
            this.activeQueryState = qid
        },
        clear() {
            this.activeQueryState = null
        }
    }

})

// Create an instance so that we can get the type of
// Store instead of StoreDefinition.
// We'll only use this for getting the type so we 
// don't have to constantly update it
let inst = ActiveQueryStore(pinia)
export type ActiveQueryStoreT = typeof inst

export default ActiveQueryStore