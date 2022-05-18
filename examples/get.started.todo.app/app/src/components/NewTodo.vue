<template>
  <div class="newTodo">
    <form @submit.prevent="addTodo()">
      <label>New ToDo </label>
      <input
          v-model="newTodo"
          name="newTodo"
          autocomplete="off"
      >
      <button>Add ToDo</button>
    </form>
  </div>
</template>

<script>
import {ref} from 'vue';
const uuid = require('uuid');

export default {
  name: 'NewTodo',
  props: {
    taskCommandUrl: String
  },
  created () {
    const newTodo = ref('');
    return {
      newTodo
    }
  },
  methods: {
    async addTodo() {
      if (this.newTodo) {
        const requestOptions = {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": uuid.v4()
          },
          body: JSON.stringify({"name": this.newTodo})
        };
        await fetch(this.taskCommandUrl, requestOptions);
        this.newTodo = '';
      }
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
