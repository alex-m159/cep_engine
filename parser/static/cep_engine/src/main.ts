
import { pinia } from './stores/query'
// @ts-ignore
import App from '@/App.vue'
import { createApp } from 'vue'
import 'bootstrap'
// import { createPinia } from 'pinia'
import { library } from "@fortawesome/fontawesome-svg-core";
import { 
    faPhone, 
    faPlay, 
    faScroll, 
    faTrash, 
    faPen, 
    faX, 
    faAngleDoubleDown, 
    faAngleDoubleUp, 
    faAngleDown,
    faAngleUp 
} from "@fortawesome/free-solid-svg-icons";

import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

library.add(faPhone);
library.add(faPlay);
library.add(faScroll);
library.add(faTrash);
library.add(faPen);
library.add(faX);
library.add(faAngleDoubleDown)
library.add(faAngleDoubleUp)
library.add(faAngleDown)
library.add(faAngleUp)

const app = createApp(App)
app.component("font-awesome-icon", FontAwesomeIcon)

app.use(pinia)

import router from './router'
app.use(router)

app.mount('#app')
