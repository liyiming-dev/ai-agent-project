<script setup lang="ts">
import { onMounted } from 'vue'
import BasicLayout from './layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const loginUserStore = useLoginUserStore()

onMounted(() => {
  void loginUserStore.fetchLoginUser()
})
</script>

<template>
  <BasicLayout />
</template>
