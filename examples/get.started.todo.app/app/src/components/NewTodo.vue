<template>
  <div class="newTodo">
    <form @submit.prevent="addTodo()">
      <label>New ToDo </label>
      <input
          v-model="newTodo"
          name="newTodo"
          ref="newTodo"
          autocomplete="off"
      >
      <button>Add ToDo</button>
    </form>
  </div>
</template>

<script>
const uuid = require('uuid');

export default {
  name: 'NewTodo',
  props: {
    taskCommandUrl: String
  },
  methods: {
    async addTodo() {
      const newTodo = this.$refs.newTodo;
      if (newTodo.value) {
        const requestOptions = {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": uuid.v4()
          },
          body: JSON.stringify({"name": newTodo.value})
        };
        await fetch(this.taskCommandUrl, requestOptions);
        newTodo.value = '';
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
