<script setup lang="ts">

import {ref, onMounted} from 'vue'
import type {Query} from '../stores/query'
import {backendIp} from '../config'

interface Props {
    query: Query
}

const props = defineProps<Props>()
const emit = defineEmits(["delete"])

let formattedQuery = ref("")

function deleteQuery() {
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query: props.query.query
        })
    }
    
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query/delete`, options)
    .then((res) => res.json())
    .then((data) => {
        if(data["ok"] == 1) {
            // delete this component
            console.log("Deleting QUERY")
            emit("delete")
        } else {
            console.log("NOT deleting query")
            alert("Could not delete query")
        }
    })
    .catch((err) => {
        console.log(err)
        alert("Error occurred while attempting to delete query")
    }) 
}

</script>

<template>
    <div>
        <div class="border border-primary p-2">
            Query ID: {{ props.query.queryId }}            
        </div>
        <br>
        <p>Submitted Query:</p>
        <div class="border border-primary p-3 text-dark">
            <p class="m-0 p-0" v-for="line in props.query.query.split('\n')">
                {{line}}
            </p>
        </div>
        <br>
        <button @click="deleteQuery()" class="btn btn-danger">
            Delete Query
            <font-awesome-icon icon="trash" />
        </button>
    </div>
    <!-- TODO: Add display to show live updating panel of query metrics -->
    <!-- TODO: Add display to show live updating panel of query matches -->
</template>

<style>
p {
    min-height: 12px;
}
</style>