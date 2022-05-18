<template>
  <img alt="Aklivity logo" src="./assets/logo.png">
  <h1>ToDo App</h1>
  <NewTodo taskCommandUrl="http://localhost:8080/tasks" />
  <TodoList taskCommandUrl="http://localhost:8080/tasks"  :tasks="tasks"/>
</template>

<script>
import NewTodo from './components/NewTodo.vue'
import TodoList from './components/TodoList.vue'

window.Buffer = window.Buffer || require('buffer').Buffer;

export default {
  name: 'App',
  components: {
    NewTodo,
    TodoList
  },
  data () {
    return {
      tasks: {}
    }
  },
  mounted() {
    let tasks = this.tasks
    let es = new EventSource('http://localhost:8080/tasks', {});
    es.addEventListener('delete', (event) => {
      let lastEventId = JSON.parse(event.lastEventId);
      let key = Buffer.from(lastEventId[0], "base64").toString("utf8");
      delete tasks[key]
    }, false);

    es.onerror = function (err) {
      if (err) {
        if (err.status === 401 || err.status === 403) {
          console.log('not authorized');
        }
      }
    };

    es.onmessage = function (event) {
      let lastEventId = JSON.parse(event.lastEventId);
      let key = Buffer.from(lastEventId[0], "base64").toString("utf8");
      let task = JSON.parse(event.data)
      tasks[key] = {"name": task.name, "etag": lastEventId[1]}
    };
  }
}
</script>

<style lang="scss">
$border: 2px solid
rgba(
    $color: white,
    $alpha: 0.35,
);
$size1: 6px;
$size2: 12px;
$size3: 18px;
$size4: 24px;
$size5: 48px;
$backgroundColor: #27292d;
$textColor: white;
$primaryColor: #0d9b76;
$secondTextColor: #1f2023;
body {
  margin: 0;
  padding: 0;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: $backgroundColor;
  color: $textColor;
  #app {
    max-width: 600px;
    margin-left: auto;
    margin-right: auto;
    padding: 20px;
    h1 {
      font-weight: bold;
      font-size: 28px;
      text-align: center;
    }
    img {
      width: 100%;
      height: auto;
    }
    form {
      display: flex;
      flex-direction: column;
      width: 100%;
      label {
        font-size: 14px;
        font-weight: bold;
      }
      input,
      button {
        height: $size5;
        box-shadow: none;
        outline: none;
        padding-left: $size2;
        padding-right: $size2;
        border-radius: $size1;
        font-size: 18px;
        margin-top: $size1;
        margin-bottom: $size2;
      }
      input {
        background-color: transparent;
        border: $border;
        color: inherit;
      }
    }
    button {
      cursor: pointer;
      background-color: $primaryColor;
      border: 1px solid $primaryColor;
      color: $secondTextColor;
      font-weight: bold;
      outline: none;
      border-radius: $size1;
      margin-left: $size1;
    }
    h2 {
      font-size: 22px;
      border-bottom: $border;
      padding-bottom: $size1;
    }
    ul {
      padding: 10px;
      li {
        display: flex;
        justify-content: space-between;
        border: $border;
        padding: $size2 $size4;
        border-radius: $size1;
        margin-bottom: $size2;
        span {
            width: 80%;
        }
        .done {
          text-decoration: line-through;
        }
        button {
          font-size: $size2;
          padding: $size1;
        }
        input {
          font-size: $size2;
          padding: $size1;
        }
        form {
          height: 10%;
        }
      }
    }
    h4 {
      text-align: center;
      opacity: 0.5;
      margin: 0;
    }
  }
}
</style>
