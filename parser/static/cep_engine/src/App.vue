<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import HelloWorld from "./components/HelloWorld.vue";
import {computed} from "vue"
import {ref, type Ref} from 'vue'
// <img
//             alt="Streamento logo"
//             class="logo"
//             src="@/assets/beaker-icon.png"
//             height="80"
//           />



/* Navigation Menu */
const router = useRouter()
const route = useRoute()

const menu_items = [
  ref({route: "/", menu_id:"home", page_name: "Home", icon: "bi-house-door", active: false}),
  ref({route: "/queries/list", menu_id:"queries", page_name: "Queries", icon: "bi-activity", active: false}),
  ref({route: "/about", menu_id:"about", page_name: "About", icon: "bi-question-circle-fill", active: false}),
]


/* This ensures the navbar is synced with the current page. */
router
.isReady()
.then(() => {
  menu_items.map((mi) => {
    console.log(`ROUTE PATH: ${route.path}`)
    if(route.path.includes(mi.value.menu_id)) {
      mi.value.active = true
    } else if(route.name === mi.value.menu_id) {
      mi.value.active = true
    } else {
      mi.value.active = false
    }
  })
})

/* This ensures the navbar is synced for all the pages that are navigated to in the future */
router.afterEach((to, from, failure) => {
  menu_items.map((mi) => {
    console.log(`ROUTE PATH: ${route.path}`)
    if(route.path.includes(mi.value.menu_id)) {
      mi.value.active = true
    } else if(route.name === mi.value.menu_id) {
      mi.value.active = true
    } else {
      mi.value.active = false
    }
  })
})

function menuClick(e: Event) {
  console.log(e)
  let menu_id: string = e.target.id
  menu_items.map((item) => {
    if(item.value.menu_id === menu_id) {
      item.value.active = true
    } else {
      item.value.active = false
    }
  })
}

</script>

<template>
  <!-- Here was trying to use the container > row > col layout that Bootstrap recommends to layout the sidebar, but it wasn't  working properly-->
  <!-- <div class="container-fluid p-0">
    <div class="row">
      <div class="col">
        <div class="menu-sidebar">
          <div class="offcanvas offcanvas-start bg-dark" tabindex="-1" id="smallMenu" aria-labelledby="smallMenu">
            <div class="offcanvas-header">
              <img
                alt="Streamento logo"
                class="logo"
                src="@/assets/beaker-icon.png"
                height="30"/>
              <h5 class="offcanvas-title text-white" id="offcanvasExampleLabel">Streamento</h5>
              <button type="button" class="btn-close text-reset btn-close-white" data-bs-dismiss="offcanvas" aria-label="Close"></button>
            </div>
            <div class="offcanvas-body">
              <ul class="nav nav-pills flex-column mb-auto">
                <li class="nav-item" v-for="item in menu_items">
                  <RouterLink :to="item.value.route" class="nav-link text-white" :class="{active: item.value.active }" aria-current="page" :id="item.value.menu_id" @click="menuClick">
                    <i class="bi me-2" :class="item.value.icon" style="font-size: 1.2;"></i>
                    {{ item.value.page_name }}
                  </RouterLink>
                </li>
              </ul>

              <ul class="text-small dropdown-menu">
                <li><a class="dropdown-item" href="#">New project...</a></li>
                <li><a class="dropdown-item" href="#">Settings</a></li>
                <li><a class="dropdown-item" href="#">Profile</a></li>
                <li><a class="dropdown-item" href="#">Sign out</a></li>
              </ul>
              <a href="#" 
                class="d-flex align-items-center text-white text-decoration-none mt-2 dropdown-toggle" 
                data-bs-toggle="dropdown" aria-expanded="false">
                <img src="https://github.com/mdo.png" alt="" class="rounded-circle me-2" width="32" height="32">
                <strong>mdo</strong>
              </a>
            </div>
          </div>

          <div class="d-md-flex d-none flex-column flex-shrink-0 p-3 text-white bg-dark collapse collapse-horizontal show">
          <a href="/" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-white text-decoration-none pe-5">
            <img
              alt="Streamento logo"
              class="logo me-2"
              src="@/assets/beaker-icon.png"
              height="30"/>
            <span class="fs-4">Streamento</span>
          </a>
          <hr>
          

          <ul class="nav nav-pills flex-column mb-auto">
            <li class="nav-item" v-for="item in menu_items">
            
              <RouterLink :to="item.value.route" class="nav-link text-white" :class="{active: item.value.active }" aria-current="page" :id="item.value.menu_id" @click="menuClick">
                <i class="bi me-2" :class="item.value.icon" style="font-size: 1.2;"></i>
                {{ item.value.page_name }}
              </RouterLink>
            </li>
          </ul>
          <hr>


            <div class="collapse" id="collapsableMenu">
              <ul class="text-small list-group list-inline">
                <li><a class="list-group-item" href="#">New project...</a></li>
                <li><a class="list-group-item" href="#">Settings</a></li>
                <li><a class="list-group-item" href="#">Profile</a></li>
                <li><a class="list-group-item" href="#">Sign out</a></li>
              </ul>
            </div>
            <a href="#collapsableMenu" 
              class="d-flex align-items-center text-white text-decoration-none mt-2" 
              data-bs-toggle="collapse" data-bs-target="#collapsableMenu" aria-expanded="false">
              <img src="https://github.com/mdo.png" alt="" class="rounded-circle me-2" width="32" height="32">
              <strong>mdo</strong>
            </a>
          </div>
          <div>
            <i class="bi bi-arrow-right-square ms-2 d-md-none" style="font-size: 2em;" data-bs-toggle="offcanvas" data-bs-target="#smallMenu" aria-controls="smallMenu"></i>
          </div>
          
        </div>
      </div>
      <div class="col">
        <RouterView class=""></RouterView>
      </div>
    </div>
  </div> -->


  <div class="">
    <div class="menu-sidebar">
      <div class="offcanvas offcanvas-start bg-dark" tabindex="-1" id="smallMenu" aria-labelledby="smallMenu">
        <div class="offcanvas-header">
          <img
            alt="Streamento logo"
            class="logo"
            src="@/assets/beaker-icon.png"
            height="30"/>
          <h5 class="offcanvas-title text-white" id="offcanvasExampleLabel">Streamento</h5>
          <button type="button" class="btn-close text-reset btn-close-white" data-bs-dismiss="offcanvas" aria-label="Close"></button>
        </div>
        <div class="offcanvas-body">
          <ul class="nav nav-pills flex-column mb-auto">
            <li class="nav-item" v-for="item in menu_items">
              <RouterLink :to="item.value.route" class="nav-link text-white" :class="{active: item.value.active }" aria-current="page" :id="item.value.menu_id" @click="menuClick">
                <i class="bi me-2" :class="item.value.icon" style="font-size: 1.2;"></i>
                {{ item.value.page_name }}
              </RouterLink>
            </li>
          </ul>

          <ul class="text-small dropdown-menu">
            <li><a class="dropdown-item" href="#">New project...</a></li>
            <li><a class="dropdown-item" href="#">Settings</a></li>
            <li><a class="dropdown-item" href="#">Profile</a></li>
            <li><a class="dropdown-item" href="#">Sign out</a></li>
          </ul>
          <a href="#" 
            class="d-flex align-items-center text-white text-decoration-none mt-2 dropdown-toggle" 
            data-bs-toggle="dropdown" aria-expanded="false">
            <img src="https://github.com/mdo.png" alt="" class="rounded-circle me-2" width="32" height="32">
            <strong>mdo</strong>
          </a>
        </div>
      </div>

      <div class="d-lg-flex d-none flex-column flex-shrink-0 p-3 text-white bg-dark collapse collapse-horizontal show">
      <a href="/" class="d-flex align-items-center mb-3 mb-md-0 me-md-auto text-white text-decoration-none pe-5">
        <img
          alt="Streamento logo"
          class="logo me-2"
          src="@/assets/beaker-icon.png"
          height="30"/>
        <span class="fs-4">Streamento</span>
      </a>
      <hr>
      

      <ul class="nav nav-pills flex-column mb-auto">
        <li class="nav-item" v-for="item in menu_items">
        
          <RouterLink :to="item.value.route" class="nav-link text-white" :class="{active: item.value.active }" aria-current="page" :id="item.value.menu_id" @click="menuClick">
            <i class="bi me-2" :class="item.value.icon" style="font-size: 1.2;"></i>
            {{ item.value.page_name }}
          </RouterLink>
        </li>
      </ul>
      <hr>


        <div class="collapse" id="collapsableMenu">
          <ul class="text-small list-group list-inline">
            <li><a class="list-group-item" href="#">New project...</a></li>
            <li><a class="list-group-item" href="#">Settings</a></li>
            <li><a class="list-group-item" href="#">Profile</a></li>
            <li><a class="list-group-item" href="#">Sign out</a></li>
          </ul>
        </div>
        <a href="#collapsableMenu" 
          class="d-flex align-items-center text-white text-decoration-none mt-2" 
          data-bs-toggle="collapse" data-bs-target="#collapsableMenu" aria-expanded="false">
          <!-- <img src="https://github.com/mdo.png" alt="" class="rounded-circle me-2" width="32" height="32"> -->
          <i class="bi bi-person-circle me-2" style="font-size: 1.2;"></i>
          <strong>username</strong>
        </a>
      </div>
      <div class="position-fixed d-lg-none">
        <!-- <i class="bi bi-arrow-right-square ms-2 d-md-none position-fixed text-bg-light" style="font-size: 2em; height: 32px; width: 32px;" data-bs-toggle="offcanvas" data-bs-target="#smallMenu" aria-controls="smallMenu"></i> -->
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" fill="currentColor" class="bi bi-arrow-right-square ms-2 mt-2 bg-light" viewBox="0 0 16 16" data-bs-toggle="offcanvas" data-bs-target="#smallMenu" aria-controls="smallMenu">
          <path fill-rule="evenodd" d="M15 2a1 1 0 0 0-1-1H2a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V2zM0 2a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V2zm4.5 5.5a.5.5 0 0 0 0 1h5.793l-2.147 2.146a.5.5 0 0 0 .708.708l3-3a.5.5 0 0 0 0-.708l-3-3a.5.5 0 1 0-.708.708L10.293 7.5H4.5z"/>
        </svg>
      </div>
        <div class="container-fluid" style="overflow: scroll;">
          <RouterView></RouterView>  
        </div>  
    </div>
  </div>
        
    

</template>

<style lang="scss">

$on-medium: 768px;

.menu-sidebar {
  display: flex;
  flex-wrap: nowrap;
  height: 100vh;
  height: -webkit-fill-available;
  max-height: 100vh;
  overflow-x: auto;
  overflow-y: scroll;
}





/* @import '@/assets/base.css';

#app {
  max-width: 1280px;
  margin: 0 auto;
  padding: 2rem;

  font-weight: normal;
}

header {
  line-height: 1.5;
  max-height: 100vh;
}

.logo {
  display: block;
  margin: 0 auto 2rem;
}

a,
.green {
  text-decoration: none;
  color: hsla(160, 100%, 37%, 1);
  transition: 0.4s;
}

.purple {
  text-decoration: none;
  color: rgb(170, 6, 192);
  transition: 0.4s;
}

@media (hover: hover) {
  a:hover {
    background-color: hsla(160, 100%, 37%, 0.2);
  }
}

nav {
  width: 100%;
  font-size: 12px;
  text-align: center;
  margin-top: 2rem;
}

nav a.router-link-exact-active {
  color: var(--color-text);
}

nav a.router-link-exact-active:hover {
  background-color: transparent;
}

nav a {
  display: inline-block;
  padding: 0 1rem;
  border-left: 1px solid var(--color-border);
}

nav a:first-of-type {
  border: 0;
}

@media (min-width: 1024px) {
  body {
    display: flex;
    place-items: center;
  }

  #app {
    display: grid;
    grid-template-columns: 1fr 1fr;
    padding: 0 2rem;
  }

  header {
    display: flex;
    place-items: center;
    padding-right: calc(var(--section-gap) / 2);
  }

  header .wrapper {
    display: flex;
    place-items: flex-start;
    flex-wrap: wrap;
  }

  .logo {
    margin: 0 2rem 0 0;
  }

  nav {
    text-align: left;
    margin-left: -1rem;
    font-size: 1rem;

    padding: 1rem 0;
    margin-top: 1rem;
  }
} */
</style>
