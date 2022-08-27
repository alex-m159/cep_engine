import { defineStore, createPinia } from "pinia"
import type { Store } from "pinia"
import { reactive } from "vue"
import piniaPersist from 'pinia-plugin-persist'

export const pinia = createPinia()

pinia.use(piniaPersist)

export interface CEPMatch {
    match: any
    offset: number
    timestamp: number
}

export interface QueryI {
    queryId: number
    query: string
    results: CEPMatch[]
    add(m: CEPMatch): void
}

export class Query implements QueryI {
    queryId: number
    threshold: number
    // sorted by offset from high to low
    results: CEPMatch[] 
    query: string

    constructor(qid: number, query: string) {
        this.queryId = qid
        this.threshold = 100
        this.results = []
        this.query = query
    }

    add(m: CEPMatch) {
        if(this.results.length === 0) {
            this.results.push(m)
            return
        }
        let lowest_offset = this.results[this.results.length - 1]
        if(lowest_offset.offset > m.offset) {
            this.results.push(m)
            if(this.results.length > this.threshold) {
                this.results.shift()
            }
            return
        }
        let highest_offset = this.results[0]
        if(m.offset > highest_offset.offset) {
            this.results.unshift(m)
            if(this.results.length > this.threshold) {
                this.results.pop()
            }
            return
        }
        for(var i = 1; i < this.results.length; i++) {
            let first = this.results[i-1]
            let second = this.results[i]
            if(first.offset > m.offset && m.offset > second.offset) {
                this.results.splice(i, 0, m)
                if(this.results.length > this.threshold) {
                    let midpoint = this.results[Math.round( this.results.length / 2)]
                    if( m.offset >= midpoint.offset) {
                        this.results.pop()
                    } else {
                        this.results.shift()
                    }
                }
                return
            }
        }
    }

    private sortFunc(a: CEPMatch, b: CEPMatch): number {
        if(a.offset < b.offset) {
            return -1
        } else if(a.offset === b.offset) {
            return 0
        } else {
            return 1
        }
    }
}


let QueryStore = defineStore('query_store', {
    state: () => ({ 
        queryList: new Map<number, QueryI>(),
        visibleQuery: undefined as unknown as number,
        threshold: 100
    }),
    getters: {
        queriesAsMap(state) {
            return state.queryList
        },
        queriesAsList(state) {
            return Array.from(state.queryList.values())
        },
        active(state): QueryI | undefined {
            if(state.visibleQuery)
                return state.queryList.get(state.visibleQuery)
            else
                return undefined
        }
    },
    actions: {
        upsertQuery(query: QueryI) {
            this.queryList.set(query.queryId, query)
        },
        deleteQuery(queryId: number) {
            return this.queryList.delete(queryId)
        },
        push(qid: number, m: CEPMatch): void  {
            if(this.queryList.has(qid) === true) {
                let result_array = (this.queryList.get(qid) as QueryI).results
                result_array.push(m)
                if(result_array.length > this.threshold) {
                    result_array.shift()
                }
            }
        },
        setActive(qid: number) {
            if(this.queryList.has(qid)) {
                this.visibleQuery = qid
            }
            
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