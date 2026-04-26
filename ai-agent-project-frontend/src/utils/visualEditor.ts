   import { onBeforeUnmount, ref, type Ref } from 'vue'

/**
 * 可视化编辑模式工具
 *
 * 主网站与 iframe 中展示的网站同域名，因此可以直接向 iframe 注入脚本，
 * 由该脚本监听鼠标悬浮 / 点击事件，并通过 postMessage 将选中元素信息回传给主网站。
 */

export interface SelectedElementInfo {
  tagName: string
  id?: string
  className?: string
  textContent?: string
  selector: string
  outerHTML?: string
}

const MESSAGE_SOURCE = 'ai-agent-visual-editor'
const STYLE_ID = '__ve_style__'

/**
 * 注入到 iframe 中执行的脚本（字符串形式）。
 * 内部使用模板字符串与正则，因此外层模板字面量需对 ` 与 ${ 进行转义。
 */
const INJECTED_SCRIPT = `(function(){
  if (window.__visualEditorInstalled) return
  window.__visualEditorInstalled = true

  var SOURCE = '${MESSAGE_SOURCE}'
  var STYLE_ID = '${STYLE_ID}'
  var HOVER_CLASS = '__ve_hover__'
  var PICKED_CLASS = '__ve_picked__'
  var enabled = false
  var pickedEl = null

  function ensureStyle(){
    if (document.getElementById(STYLE_ID)) return
    var s = document.createElement('style')
    s.id = STYLE_ID
    s.textContent = '.' + HOVER_CLASS + '{outline:2px dashed #1677ff !important;outline-offset:-2px;cursor:pointer !important}'
      + '.' + PICKED_CLASS + '{outline:2px solid #f5222d !important;outline-offset:-2px;box-shadow:0 0 0 4px rgba(245,34,45,0.12) !important}'
    document.head.appendChild(s)
  }

  function buildSelector(el){
    if (!el || el === document.body) return 'body'
    if (el.id) return '#' + el.id
    var parts = []
    var cur = el
    while (cur && cur !== document.body && parts.length < 4){
      var part = cur.tagName.toLowerCase()
      if (cur.className && typeof cur.className === 'string'){
        var cls = cur.className.trim().split(/\\s+/).filter(function(c){ return c && c.indexOf('__ve_') !== 0 }).slice(0, 2).join('.')
        if (cls) part += '.' + cls
      }
      var parent = cur.parentElement
      if (parent){
        var idx = Array.prototype.indexOf.call(parent.children, cur) + 1
        part += ':nth-child(' + idx + ')'
      }
      parts.unshift(part)
      cur = cur.parentElement
    }
    return parts.join(' > ')
  }

  function summarize(el){
    var cls = (typeof el.className === 'string' ? el.className : '')
      .replace(/\\b__ve_[\\w]+\\b/g, '').replace(/\\s+/g, ' ').trim()
    var html = (el.outerHTML || '').replace(new RegExp('\\\\s*class="[^"]*' + HOVER_CLASS + '[^"]*"', 'g'), '')
                                    .replace(new RegExp('\\\\s*class="[^"]*' + PICKED_CLASS + '[^"]*"', 'g'), '')
    return {
      tagName: el.tagName.toLowerCase(),
      id: el.id || undefined,
      className: cls || undefined,
      textContent: (el.textContent || '').trim().slice(0, 80),
      selector: buildSelector(el),
      outerHTML: html.slice(0, 500)
    }
  }

  function clearHover(){
    var nodes = document.querySelectorAll('.' + HOVER_CLASS)
    for (var i = 0; i < nodes.length; i++) nodes[i].classList.remove(HOVER_CLASS)
  }
  function clearPicked(){
    if (pickedEl) pickedEl.classList.remove(PICKED_CLASS)
    pickedEl = null
  }

  function onOver(e){
    if (!enabled || !e.target || e.target === document.body) return
    clearHover()
    if (e.target !== pickedEl) e.target.classList.add(HOVER_CLASS)
  }
  function onOut(e){
    if (!enabled || !e.target) return
    e.target.classList.remove(HOVER_CLASS)
  }
  function onClick(e){
    if (!enabled || !e.target) return
    e.preventDefault()
    e.stopPropagation()
    clearPicked()
    pickedEl = e.target
    pickedEl.classList.remove(HOVER_CLASS)
    pickedEl.classList.add(PICKED_CLASS)
    try {
      window.parent.postMessage({ source: SOURCE, type: 'select', payload: summarize(pickedEl) }, '*')
    } catch (err) {}
  }

  document.addEventListener('mouseover', onOver, true)
  document.addEventListener('mouseout', onOut, true)
  document.addEventListener('click', onClick, true)

  window.addEventListener('message', function(e){
    var data = e.data
    if (!data || data.source !== SOURCE) return
    if (data.type === 'enable'){
      enabled = true
      ensureStyle()
    } else if (data.type === 'disable'){
      enabled = false
      clearHover()
      clearPicked()
    } else if (data.type === 'clear'){
      clearPicked()
    }
  })

  // 通知父窗口脚本已就绪
  try { window.parent.postMessage({ source: SOURCE, type: 'ready' }, '*') } catch (err) {}
})();`

/**
 * 将选中的元素信息拼接为可以追加到提示词后面的描述
 */
export function buildSelectedElementPrompt(info: SelectedElementInfo): string {
  const lines = [
    '【可视化选中元素】请基于该元素进行修改：',
    `- 标签：${info.tagName}`,
    info.id ? `- ID：${info.id}` : '',
    info.className ? `- 类名：${info.className}` : '',
    info.selector ? `- 选择器：${info.selector}` : '',
    info.textContent ? `- 文本内容：${info.textContent}` : '',
    info.outerHTML ? `- HTML 片段：${info.outerHTML}` : '',
  ].filter(Boolean)
  return lines.join('\n')
}

export function useVisualEditor(iframeRef: Ref<HTMLIFrameElement | null>) {
  const editMode = ref(false)
  const selectedElement = ref<SelectedElementInfo | null>(null)

  const postToIframe = (type: 'enable' | 'disable' | 'clear') => {
    const win = iframeRef.value?.contentWindow
    if (!win) return
    try {
      win.postMessage({ source: MESSAGE_SOURCE, type }, '*')
    } catch {
      // ignore
    }
  }

  /**
   * 向 iframe 注入交互脚本。同域名下可直接访问 contentDocument。
   */
  const injectScript = () => {
    const iframe = iframeRef.value
    if (!iframe) return
    try {
      const doc = iframe.contentDocument
      if (!doc || !doc.documentElement) {
        console.warn('[visualEditor] iframe 不可访问，可能是跨域或尚未加载')
        return
      }
      const win = iframe.contentWindow as (Window & { __visualEditorInstalled?: boolean }) | null
      if (win?.__visualEditorInstalled) return
      const script = doc.createElement('script')
      script.textContent = INJECTED_SCRIPT
      doc.documentElement.appendChild(script)
    } catch (err) {
      // 跨域时访问 contentDocument 会抛 SecurityError
      console.warn('[visualEditor] 注入脚本失败，请确认主站与 iframe 同源：', err)
    }
  }

  const onMessage = (e: MessageEvent) => {
    const data = e.data as { source?: string; type?: string; payload?: SelectedElementInfo } | null
    if (!data || data.source !== MESSAGE_SOURCE) return
    if (data.type === 'select' && data.payload) {
      selectedElement.value = data.payload
    } else if (data.type === 'ready' && editMode.value) {
      // iframe 内部脚本就绪后，如果当前处于编辑模式，立即开启
      postToIframe('enable')
    }
  }

  const enableEdit = () => {
    if (editMode.value) return
    editMode.value = true
    injectScript()
    postToIframe('enable')
  }

  const disableEdit = () => {
    selectedElement.value = null
    if (editMode.value) {
      editMode.value = false
      postToIframe('disable')
    }
  }

  const toggleEdit = () => {
    if (editMode.value) disableEdit()
    else enableEdit()
  }

  const clearSelection = () => {
    selectedElement.value = null
    postToIframe('clear')
  }

  /**
   * 在 iframe 重新加载（如刷新预览或预览版本变化）后调用，重新注入脚本。
   */
  const handleIframeLoad = () => {
    if (!editMode.value) return
    injectScript()
    postToIframe('enable')
  }

  window.addEventListener('message', onMessage)
  onBeforeUnmount(() => {
    window.removeEventListener('message', onMessage)
  })

  return {
    editMode,
    selectedElement,
    enableEdit,
    disableEdit,
    toggleEdit,
    clearSelection,
    handleIframeLoad,
  }
}
