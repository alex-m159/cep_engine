<script setup lang="ts">

import type {QueryI, CEPMatch, EventTypes} from '../stores/query'
import {ref, onMounted, onUnmounted, type InputHTMLAttributes} from 'vue'
import {backendIp} from '../config'

interface Props {
    event_types: EventTypes[],
    query_id: number
}

interface EventData {
    event_name: string, 
    event_fields: Map<string, number | string>
}

const props = defineProps<Props>()

let event_forms = ref(new Map<string, EventData>())

function setSelection(form_id: string, event_name: any) {
    console.log("setting selection")
    let selected = props.event_types.find(et => et.event_name === event_name) as EventTypes
    let form = event_forms.value.get(form_id) as EventData
    let previous = props.event_types.find(et => et.event_name === form.event_name) as EventTypes

    let fields_match = selected.event_fields.every((selected_field) => {
        if(previous)
            return previous.event_fields.includes(selected_field)
        else
            return false
    })
    // We have to clear out old fields if the field names don't match
    if(!fields_match) {
        console.log("clearing values")
        let event_field_values = new Map<string, number | string>()
        selected.event_fields.forEach((field_name) => {
            // @ts-ignore
            event_field_values.set(field_name, "")
        })
        form = {
            "event_name": selected.event_name,
            "event_fields": event_field_values
        }
    } else {
        console.log("not clearing values")
        form["event_name"] = selected.event_name
    }
    console.log("saving result")
    event_forms.value.set(form_id, form)
}

function updateEventInput(form_id: string, event_name: string, event_field: string, field_value: string | number) {
    if(event_forms.value.has(form_id)) {
        let form: EventData = event_forms.value.get(form_id) as EventData
        form.event_fields.set(event_field, field_value)
        // form[event_field] = field_value
        event_forms.value.set(form_id, form)
    }
    
}

function submitEventInputs() {
    console.log(`Submit button was clicked.`)
    console.log(`Current form data:`)
    console.log(event_forms.value)
    let events: [string, {field_name: string, field_value: string}[]][] = Array.from(event_forms.value.entries()).map((elem) => {
        let form_id = elem[0]
        let event_data: EventData = elem[1]
        let field_objects = Array.from(event_data.event_fields.entries()).map((elem) => {
            return {
                "field_name": elem[0],
                "field_value": String(elem[1])
            }
        })
        return [event_data.event_name, field_objects]
    })

    console.log("Sending this to backend:")
    console.log(JSON.stringify({
            query_id: props.query_id,
            events: events
        }))
    let options = {
        method: 'POST',
        headers: new Headers(),
        body: JSON.stringify({
            query_id: props.query_id,
            events: events
        })
    }
    options.headers.set('Content-Type', 'application/json')
    fetch(`http://${backendIp}/query/send_events`, options)
    // .then((res) => res.json())
    .then((res) => res.json() )
    .then((data) => {
        console.log(`Event input response:`)
        console.log(data)
    })

}

function eventFormId() {
    return `${Date.now()}:${event_forms.value.size}`
}

function addEventInputForm() {
    let key = eventFormId()
    let event_field_values = new Map<string, number | string>()
    props.event_types[0].event_fields.forEach((field_name) => {
        // @ts-ignore
        event_field_values.set(field_name, "")
    })
    let form: EventData = {
        "event_name": props.event_types[0].event_name,
        "event_fields": event_field_values
    }
    event_forms.value.set(key, form)
}

function deleteForm(form_id: string) {
    event_forms.value.delete(form_id)
}
</script>

<template>
  <div v-for="[form_id, form] in event_forms">
    <select @change="e => setSelection(form_id, (e.target as HTMLInputElement).value)">
      <option
        v-for="et in props.event_types"
        :value="[[et.event_name]]"
        :selected="form.event_name === et.event_name ? true : false"
      >
        {{ et.event_name }}
      </option>
    </select>
    <!-- <span v-if="event_forms.get(form_id)["event_name"] !== undefined">
        </span> -->
    <span v-for="field in form.event_fields.keys()" :key="form_id">
      <input
        @input="(e) => updateEventInput(form_id, form.event_name, field, (e.target as HTMLInputElement).value)"
        class="form-control event-type-inputs mx-1"
        cols="4"
        type="text"
        :placeholder="field"
        :value="form.event_fields.get(field)"
      />
    </span>
    <button @click="deleteForm(form_id)" class="btn btn-outline-danger m-2">
      Remove -
    </button>
    <!-- <button @click="submitEventInputs()" class="mx-2 btn btn-primary">Submit</button> -->
  </div>
  <div>
    <button 
        @click="addEventInputForm()" 
        class="btn btn-primary">
        Add Event +
    </button>
    <button 
        @click="submitEventInputs()" 
        class="btn btn-success mx-2" 
        :disabled="event_forms.size > 0 ? false : true">
        Submit
    </button>
  </div>
</template>
