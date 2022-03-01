<script setup lang="ts">
import { RouterLink, RouterView } from "vue-router";
import HelloWorld from "./components/HelloWorld.vue";
// This is bootstrap 5 NPM package. If this starts
// to fail, switch to "BootstrapVue" package
import "bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";

</script>

<template>
  <div class="container-fluid app-header">
    <nav class="navbar navbar-dark navbar-expand-md">
      <div class="container">
        <a class="navbar-brand" href="#">
          <img
            alt="Streamento logo"
            class="logo"
            src="@/assets/beaker-icon.png"
            height="80"
          />
        </a>
        <button
          class="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarSupportedContent"
          aria-controls="navbarSupportedContent"
          aria-expanded="false"
          aria-label="Toggle navigation"
        >
          <span class="navbar-toggler-icon"></span>
        </button>

        <div class="collapse navbar-collapse" id="navbarSupportedContent">
          <div class="collapse navbar-collapse" id="navbarSupportedContent">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
              <li class="nav-item">
                <!-- <a class="nav-link active" aria-current="page" href="#">Home</a> -->
                <RouterLink to="/" class="nav-link active text-light">Home</RouterLink>
              </li>
              <li class="nav-item">
                <!-- <a class="nav-link" href="#">Link</a> -->
                <RouterLink to="/about" class="nav-link active text-light"
                  >About</RouterLink
                >
              </li>
              <li class="nav-item">
                <RouterLink to="/queries" class="nav-link active text-light"
                  >Queries</RouterLink
                >
              </li>
            </ul>
          </div>
          <form class="d-flex">
            <input
              class="form-control me-2"
              type="search"
              placeholder="Search"
              aria-label="Search"
            />
            <button class="btn btn-outline-light" type="submit">Search</button>
          </form>
        </div>

        <!-- <div class="wrapper">
            <HelloWorld msg="Streamento Event Engine" />

            <div class="collapse navbar-collapse" id="navbarSupportedContent">
              <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                <li class="nav-item">
                  <a class="nav-link active" aria-current="page" href="#">Home</a>
                  <RouterLink to="/" class="nav-link active">Home</RouterLink>
                </li>
                <li class="nav-item">
                  <a class="nav-link" href="#">Link</a>
                  <RouterLink to="/about" class="nav-link active">About</RouterLink>
                </li>
                <li class="nav-item">
                  <RouterLink to="/queries" class="nav-link active">Queries</RouterLink>
                </li>
              </ul>
            </div>
          </div> -->
      </div>
    </nav>
  </div>
  <div class="container">
    <RouterView />
  </div>
</template>

<style lang="scss">
// @import "scss/_variables.scss";
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

// @import "bootstrap/scss/mixins";
// @import "bootstrap/scss/utilities";
// @import "bootstrap/scss/helpers";
// @import "bootstrap/scss/functions";
// @import "bootstrap/scss/variables";

.app-header {
  background-color: $purple-600;
}
</style>

<style>
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
