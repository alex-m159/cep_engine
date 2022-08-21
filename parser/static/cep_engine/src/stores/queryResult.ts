import { pinia } from "./query";
import { defineStore } from "pinia"
import {reactive} from 'vue'

export interface CEPMatch {
    match: any
    offset: number
    timestamp: number
}

export let QueryResultStore = defineStore('query_result_store', {
    state: () => ({ 
        queryResults: new Map<number, Array<CEPMatch>>(),
        threshold: 100
    }),
    getters: {
        asMap(state) {
            return state.queryResults
        },
        asList(state) {
            return Array.from(state.queryResults.values())
        }
    },
    actions: {
        push(qid: number, result: CEPMatch) {
            if(!this.queryResults.has(qid) || this.queryResults.get(qid) == undefined) {
                this.queryResults.set(qid, [])
            }
            
            let result_array: any[] = this.queryResults.get(qid) as any[]
            result_array.push(result)
            if(result_array.length > this.threshold) {
                result_array.shift()
            }
        }
    }

})


export let queryResultStore = reactive(QueryResultStore(pinia))
export type QueryResultStoreT = typeof queryResultStore

export default queryResultStore