<template>
  <footer class="global-footer">
    <div class="footer-content">
      <span class="footer-divider" aria-hidden="true"></span>
      <div class="footer-text">
        <span class="footer-mark">AI&nbsp;Agent</span>
        <span class="footer-sep">·</span>
        <span>编程导航原创项目 by</span>
        <a href="https://www.codefather.cn" target="_blank" rel="noopener noreferrer">
          程序员鱼皮
        </a>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.global-footer {
  margin-top: auto;
  position: relative;
}

.footer-content {
  width: min(calc(100% - 40px), 1240px);
  margin: 0 auto;
  padding: 28px 0 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.footer-divider {
  width: 160px;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(15, 123, 138, 0.3),
    transparent
  );
}

.footer-text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-500);
  font-size: 13px;
  letter-spacing: 0.02em;
}

.footer-mark {
  font-family: var(--font-display);
  font-style: italic;
  font-weight: 500;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.footer-sep {
  color: var(--ink-300);
}

a {
  color: var(--teal-700);
  font-weight: 600;
  position: relative;
  transition: color 0.22s var(--ease-out-quart);
}

a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 1px;
  background: currentColor;
  transform: scaleX(0);
  transform-origin: right;
  transition: transform 0.3s var(--ease-out-expo);
}

a:hover::after {
  transform: scaleX(1);
  transform-origin: left;
}
</style>
