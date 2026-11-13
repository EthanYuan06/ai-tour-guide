### 阶段一：Node 构建静态产物 ###
# vite 8 engines 为 ^20.19 || >=22.12，node:22-alpine 满足；
# 若构建报 @rolldown/binding-linux-x64-musl 缺失（musl 原生二进制问题），
# 构建时用 --build-arg NODE_IMAGE=node:22（Debian 底 glibc）即可，功能等价只是体积大
ARG NODE_IMAGE=node:22-alpine
FROM ${NODE_IMAGE} AS builder
WORKDIR /build

# lockfile 驱动安装，保证可重现；npmmirror 为国内镜像源
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com

COPY . .
ENV NODE_OPTIONS=--max-old-space-size=768
RUN npm run build

### 阶段二：Nginx 托管 dist 并反代 /api ###
FROM nginx:stable-alpine AS runtime
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
