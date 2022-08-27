<script setup lang="ts">

interface Props {
    msg: string
    close_fn: Function,
    alert_type?: "primary" | "secondary" | "success" | "danger" | "warning" | "info" | "light" | "dark"
}
const props = defineProps<Props>()

const alert_class = props.alert_type === undefined ? "alert alert-danger alert-dismissable" : `alert alert-${props.alert_type} alert-dismissable`
</script>

<template>
    <div :class="alert_class" role="alert">
        {{props.msg}}
        <button type="button" class="btn-close float-end" aria-label="Close" @click="props.close_fn()"></button>
    </div>
</template>
