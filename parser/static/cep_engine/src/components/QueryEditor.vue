<script setup lang="ts">
import {onMounted, onUnmounted, ref, type Ref} from 'vue'
import {type QueryStoreT, Query, type QueryI} from '../stores/query'
import useQueryStore from "../stores/query"
import {createEditor} from "../editor/MonacoEditor"
import {backendIp} from '../config' 
import type {editor} from "monaco-editor"
import router from '@/router'


const queryStore = useQueryStore()
const error_message = ref("")
const output = ref("")
const query = ref('')

function submitQuery() {

    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query: getEditorValue()
        })
    }
    
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query`, options)
    .then((resp) => resp.json())
    .then((data) => {
        console.log("Query Submission Response:")
        console.log(JSON.stringify(data))
        // let queryToAdd: Query = {
        //     queryId: data["query_id"],
        //     query: query.value
        // }
        let queryToAdd = new Query(Number(data["query_id"]), query.value)
        if(data['ok'] === 1) {
            queryStore.upsertQuery(queryToAdd)
            query.value = ""
            router.push(`/queries/${data['query_id']}`)
        } else {
            error_message.value = data['err']
        }
    })
    .catch((err) => {
        console.log("Query Submission Error")
        console.log(err)
    })
}

function parseQuery() {
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query: query.value
        })
    }
    
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query/ast`, options)
    .then((resp) => resp.json())
    .then((data) => {
        console.log("Query Submission Response:")
        console.log(JSON.stringify(data))
        if(data['ok'] === 1) {
            output.value = JSON.stringify(JSON.parse(data["ast"]), null, 2)
        } else {
            error_message.value = data['err']
        }
    })
    .catch((err) => {
        console.log("Query Submission Error")
        console.log(err)
    })
}







const monaco_container: Ref<HTMLElement> = ref(null as unknown as HTMLElement)
var query_editor: editor.IStandaloneCodeEditor = null as unknown as editor.IStandaloneCodeEditor

onMounted(() => {
    query_editor = createEditor(monaco_container.value)
})


/* Used for testing to reduce typing */
function getCode() {
	return [
        "type A(field1: integer, field2: string)",
        "type B(field1: integer, field2: string)",
        "type C(field1: integer, field2: string)",
        "",
        "EVENT SEQ(A a, B b, C c)",
        "WHERE a.field1 > 100 AND b.field2 = 'some string'",
        "WITHIN 5 minutes"
	].join("\n");
}


function getEditorValue() {
    if(query_editor !== null) {
        let query_string = query_editor.getValue()
        console.log(`Query String: ${query_string}`)
        return query_string
    }
    return ""
}




</script>

<template>

    <div class="row my-4">
        <div class="col">
            <h3>Query Creator</h3>
        </div>
    </div>
    <div class="row my-4">
        <div class="col-md-10">
            <!-- <textarea 
                class="form-control" 
                rows="8" 
                cols="40" 
                v-model="query" 
                :class="{'border': (error_message !== ''), 'border-danger': (error_message !== ''), 'error-editor': (error_message !== '')} ">
            </textarea> -->
            <div id="container" ref="monaco_container" style="height: 60vh;">
            </div>    
        </div>
        <div class="col-md-2 d-flex justify-content-center flex-column">
            <button @click="submitQuery()" type="submit" class="btn btn-outline-dark m-2">
                <font-awesome-icon icon="play" />
                Submit Query
            </button>
            <button class="btn btn-outline-dark m-2" @click="parseQuery()">
                <font-awesome-icon icon="scroll" />
                Parse Query
            </button>
            <button class="btn btn-outline-danger m-2" @click="$router.back()">
                <font-awesome-icon icon="x" />
                Cancel
            </button>
        </div>
    </div>
    
    <div 
        class="row my-4 text-danger" 
        v-if="error_message !== ''">
        <div class="col">
            {{error_message}}
        </div>
    </div>
    <div 
        class="row my-4"
        v-if="output !== ''">
        <div class="col">
            {{ output }}
        </div>        
    </div>
    <div class="row">
        <div class="col">
            
        </div>
    </div>
    

</template>
