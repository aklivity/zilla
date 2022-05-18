<template>
  <li>
    <span class=".name" v-if="!isEditing">{{ name }}</span>
    <form v-else class=".editform" @submit.prevent="finishEditing()">
      <input
          type="text"
          class="form-control"
          v-model="newTodoName"
          @blur="finishEditing()"
          ref="newTodo"
      />
    </form>
    <button @click="startEditing()">Edit</button>
    <button @click="$emit('on-delete')">Delete</button>
  </li>
</template>

<script>
export default {
  name: 'ToDo',
  data() {
    return {
      isEditing: false,
      newTodoName: ""
    };
  },
  props: {
    index: String,
    name: String
  },
  methods: {
    startEditing() {
      if (this.isEditing) {
        this.finishEditing();
      } else {
        this.newTodoName = this.name;
        this.isEditing = true;
        this.$nextTick(() => this.$refs.newTodo.focus());
      }
    },
    finishEditing() {
      this.isEditing = false;
      this.$emit("on-edit", this.newTodoName);
    }
  }
};
</script>

<style lang="scss" scoped>
</style>
