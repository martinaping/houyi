# admin-web

后羿（Houyi）管理后台前端。

- React 19.3 + TypeScript 5.9（strict）
- Ant Design 6.7 + Vite 7.3
- React Router 7、TanStack Query 5、i18next
- 版本：`0.8.0`

## 本地开发

```bash
cd admin-web
yarn install

# 可选：覆盖 API 地址，默认走 Vite 代理到 127.0.0.1:18081
cp .env.example .env.local

yarn dev
```

访问 http://localhost:5173 ，使用 admin-server 配置的 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 登录。

## 构建与检查

```bash
yarn build   # tsc --noEmit + vite build，产物在 dist/
yarn lint    # ESLint 9 flat config
yarn test    # Vitest
```

## 目录

```
src/
├── api/          # 类型、请求封装、错误本地化
├── auth/         # 登录状态与路由守卫
├── components/   # 布局等通用组件
├── i18n/         # 中英文文案
├── pages/        # 概览 / 健康巡检 / 企业 / 消息 / 队列 / 系统设置 / 审计日志 / 数据清理 / 系统信息 / 登录
└── utils/        # 格式化工具
```

## 部署

1. `yarn build` 后将 `dist/` 放到 Nginx 静态目录。
2. 将 `/api` 反向代理到 admin-server（默认 `127.0.0.1:18081`）。
3. SPA 路由需要 fallback 到 `index.html`，参考 [nginx.conf.example](nginx.conf.example)。

生产环境建议同源部署，此时无需开放 CORS；若前后端分域，通过 `ADMIN_CORS_ORIGINS` 配置允许来源。

## 约定

- 用户可见文案统一走 i18n，不在组件里写死中文/英文。
- 服务端状态使用 React Query 管理，变更后按 key 失效缓存。
- 所有请求通过 `src/api/client.ts`，access token 只保存在内存，刷新页面用 HttpOnly cookie 恢复会话。
- 前端版本需与 admin-server 保持 `0.8.0`。
