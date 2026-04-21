export interface MenuConfigItem {
  key: string
  label: string
}

export const globalMenuItems: MenuConfigItem[] = [
  {
    key: '/',
    label: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
  },
  {
    key: '/admin/chatHistoryManage',
    label: '对话管理',
  },
  {
    key: '/about',
    label: '关于',
  },
]
