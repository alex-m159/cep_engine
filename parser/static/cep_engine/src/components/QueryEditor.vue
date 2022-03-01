<script setup lang="ts">
import {onMounted, onUnmounted, ref, type Ref} from 'vue'
import type {QueryStoreT, Query} from '../stores/query'
 

let query = ref("")

interface Props {
    queryStore: QueryStoreT
}

const props = defineProps<Props>()

function submitQuery() {
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query: query.value
        })
    }
    options.headers.set('Content-Type', 'application/json')
    fetch("http://localhost:5000/query", options)
    .then((resp) => resp.json())
    .then((data) => {
        console.log("Query Submission Response:")
        console.log(JSON.stringify(data))
        let queryToAdd: Query = {
            queryId: data["query_id"],
            query: query.value
        }
        if(data['ok'] === 1) {
            props.queryStore.upsertQuery(queryToAdd)
            query.value = ""
        } else {
            alert("Query submission failed")
        }
    })
    .catch((err) => {
        console.log("Query Submission Error")
        console.log(err)
    })
}

</script>

<template>

    <div>
        <div>
            <textarea class="form-control" rows="8" cols="40" v-model="query"></textarea>
        </div>
        <div class="my-2">
            <button @click="submitQuery()" type="submit" class="btn btn-primary">
                Submit Query
                <font-awesome-icon icon="play" />
            </button>
            <button class="btn btn-info mx-3">
                Parse Query
                <font-awesome-icon icon="scroll" />
            </button>
        </div>
        
        
        
    </div>

</template>

<style lang="scss">

@import "../node_modules/bootstrap/scss/functions";
@import "../node_modules/bootstrap/scss/variables";
@import "../node_modules/bootstrap/scss/mixins";

// 4. Include any optional Bootstrap components as you like
@import "../node_modules/bootstrap/scss/root";
@import "../node_modules/bootstrap/scss/reboot";
@import "../node_modules/bootstrap/scss/type";
@import "../node_modules/bootstrap/scss/images";
@import "../node_modules/bootstrap/scss/containers";
@import "../node_modules/bootstrap/scss/grid";



.blue-500 {
    background-color: $blue-500;
    border-color: $blue-500;
    color: white
}

.blue-400 {
    background-color: $blue-400;
    border-color: $blue-400;
    color: white
}

</style>