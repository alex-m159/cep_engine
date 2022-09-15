<script setup lang="ts">

import type {QueryI, CEPMatch, EventTypes} from '../stores/query'
import {ref, onMounted, onUnmounted, type InputHTMLAttributes} from 'vue'

interface Props {
    event_types: EventTypes[]
}

interface FormData {
    event_name: string, 
    event_fields: Map<string, number | string>
}

const props = defineProps<Props>()
const emit = defineEmits(["delete", "update"])

let event_forms = ref(new Map<string, any>())

function setSelection(form_id: string, event_name: any) {
    console.log("setting selection")
    let selected = props.event_types.find(et => et.event_name === event_name) as EventTypes
    let form = event_forms.value.get(form_id)
    var previous: EventTypes | undefined = undefined
    if("event_name" in form) {
        previous = props.event_types.find(et => et.event_name === form["event_name"]) as EventTypes
    }
    let fields_match = selected.event_fields.every((selected_field) => {
        if(previous)
            return previous.event_fields.includes(selected_field)
        else
            return false
    })
    // We have to clear out old fields if the field names don't match
    if(previous && !fields_match) {
        console.log("clearing values")
        let event_field_values = {}
        selected.event_fields.forEach((field_name) => {
            // @ts-ignore
            event_field_values[field_name] = ""
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
    let form = event_forms.value.get(form_id)
    form["event_fields"][event_field] = field_value
    // form[event_field] = field_value
    event_forms.value.set(form_id, form)
}

function submitEventInputs() {
    console.log(`Submit button was clicked.`)
    console.log(`Current form data:`)
    console.log(event_forms.value)

}

function eventFormId() {
    return `${Date.now()}:${event_forms.value.size}`
}

function addEventInputForm() {
    let key = eventFormId()
    let event_field_values = {}
    props.event_types[0].event_fields.forEach((field_name) => {
        // @ts-ignore
        event_field_values[field_name] = ""
    })
    let form = {
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
        :selected="form['event_name'] === et.event_name ? true : false"
      >
        {{ et.event_name }}
      </option>
    </select>
    <!-- <span v-if="event_forms.get(form_id)["event_name"] !== undefined">
        </span> -->
    <span v-for="field in Object.keys(form.event_fields)" :key="form_id">
      <input
        @input="(e) => updateEventInput(form_id, form.event_name, field, (e.target as HTMLInputElement).value)"
        class="form-control event-type-inputs mx-1"
        cols="4"
        type="text"
        :placeholder="field"
        :value="form.event_fields[field]"
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
