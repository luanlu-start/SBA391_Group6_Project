# FPTPost frontend

React 18.3, React Router 6, TypeScript 5.9 và Vite 5.4. Dùng Node.js 21.7.x
theo lựa chọn stack của nhóm; Dockerfile sử dụng Node 21.

Từ thư mục client:

```powershell
npm ci
npm run dev
```

Trước khi gửi PR: npm run typecheck, npm run lint, npm run build.
Vite proxy /api tới backend khi phát triển; Docker dùng Nginx.

Quy ước và tài liệu nghiệp vụ xem [README dự án](../README.md),
[CONTRIBUTING](../docs/CONTRIBUTING.md) và [API](../docs/API.md).
