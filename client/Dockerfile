# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_URL=/api/v1
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run lint && npm run build

FROM nginx:1.28-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
