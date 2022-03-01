<script setup lang="ts">

import type {QueryStoreT} from '../stores/query'
import QueryListItem from '../components/QueryListItem.vue'

interface Props {
    queryStore: QueryStoreT
}

const props = defineProps<Props>()

function deleteItem(queryId: number) {
    console.log("Deleting query from Pinia Store")
    console.log(queryId)
    props.queryStore.deleteQuery(queryId)
}

</script>
<template>
<div v-for="query in props.queryStore.queriesAsList" class="accordion" id="accordionExample">
        <div class="accordion-item">
            <h2 class="accordion-header" :id="`heading-${query.queryId}`">
                <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" :data-bs-target="`#collapse-${query.queryId}`" aria-expanded="false" :aria-controls="`collapse-${query.queryId}`">
                    Query: {{query.queryId}}
                </button>
            </h2>
            <div :id="`collapse-${query.queryId}`" class="accordion-collapse collapse" :aria-labelledby="`heading-${query.queryId}`" data-bs-parent="#accordionExample">
                <div class="accordion-body">
                    <QueryListItem
                     :query="query"
                     @delete="deleteItem(query.queryId)"
                    ></QueryListItem>
                </div>
            </div>
        </div>
    </div>
</template>