<template>
<h2>ToDo List</h2>
  <ul  ref="tasks">
    <todo
        v-for="(task, key) in tasks"
        :key="key"
        :name="task.name"
        @on-delete="removeTodo(key)"
        @on-edit="editTodo(key, task.etag, $event)"
    />
  </ul>
</template>


<script>
import Todo from "./Todo.vue";
import {ref} from 'vue';

const uuid = require('uuid');

export default {
  name: 'TodoList',
  props: {
    taskCommandUrl: String,
    tasks: {
      required: true,
      type: Object
    }
  },
  components: {
    Todo
  },
  created () {
    const newTodo = ref('');
    return {
      newTodo
    }
  },
  methods: {
    async editTodo(key, etag, newTodoName) {
      const requestOptions = {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": uuid.v4(),
          "If-Match": etag
        },
        body: JSON.stringify({"name": newTodoName})
      };
      await fetch(`${this.taskCommandUrl}/${key}`, requestOptions);
    },
    async removeTodo(key) {
      const requestOptions = {
        method: "DELETE",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": uuid.v4()
        }
      };
      await fetch(`${this.taskCommandUrl}/${key}`, requestOptions);
    }
  },
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
