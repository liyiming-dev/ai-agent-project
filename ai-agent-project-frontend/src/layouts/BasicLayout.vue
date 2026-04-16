<script setup lang="ts">
import { RouterView } from 'vue-router'
import { Layout, LayoutContent, LayoutFooter, LayoutHeader } from 'ant-design-vue'

import GlobalFooter from '@/components/GlobalFooter.vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
</script>

<template>
  <Layout class="basic-layout">
    <LayoutHeader class="layout-header">
      <GlobalHeader />
    </LayoutHeader>

    <LayoutContent class="layout-content">
      <main class="content-inner">
        <RouterView />
      </main>
    </LayoutContent>

    <LayoutFooter class="layout-footer">
      <GlobalFooter />
    </LayoutFooter>
  </Layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
  background: transparent;
}

.layout-header {
  height: auto;
  padding: 0;
  line-height: normal;
  background: transparent;
}

.layout-content {
  flex: 1;
  padding: 32px 0 40px;
  background: transparent;
}

.content-inner {
  width: min(calc(100% - 32px), 1200px);
  margin: 0 auto;
}

.layout-footer {
  padding: 0;
  background: transparent;
}

@media (max-width: 768px) {
  .layout-content {
    padding: 24px 0 32px;
  }

  .content-inner {
    width: min(calc(100% - 24px), 1200px);
  }
}
</style>
