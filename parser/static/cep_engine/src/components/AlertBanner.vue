<script setup lang="ts">

interface Props {
    msg: string
    alert_type?: "primary" | "secondary" | "success" | "danger" | "warning" | "info" | "light" | "dark"
}
const props = defineProps<Props>()

const alert_class = props.alert_type === undefined ? "alert alert-danger" : `alert alert-${props.alert_type}`
</script>

<template>
    <div :class="alert_class" role="alert">
        {{props.msg}}
    </div>
</template>
