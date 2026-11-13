# ai-tour-guide 腾讯云轻量服务器 + 宝塔面板 Docker 部署手册

前置状态（已完成，本手册不再涉及）：轻量服务器与宝塔面板就绪、Docker 与镜像加速已配置、防火墙已放行 80/443、域名 `yuluo-ai.site` 已备案并解析到服务器。

整体链路（先建立心智模型，再动手）：

```
用户 → https://yuluo-ai.site
     → 宝塔 Nginx 站点（Let's Encrypt SSL、强制 HTTPS、整站反向代理）
     → 127.0.0.1:18080  frontend 容器（nginx 托管静态页 + 反代 /api，关 SSE 缓冲）
     → backend 容器（Spring Boot :8123，context-path /api，SSE 超时 300s）
     → mysql 容器（8.4，仅容器网络内部可达，数据存卷）
外部依赖：阿里云百炼 HTTPS API（服务器出方向访问，无需开任何入站端口）
```

所有业务容器只绑 `127.0.0.1` 高位端口，公网唯一入口是宝塔 Nginx 的 80/443。

参考的官方文档：

- [宝塔面板文档：添加网站](https://docs.bt.cn/user-guide/site/php/create-web)
- [宝塔面板文档：反向代理](https://docs.bt.cn/user-guide/site/php/site-config/reverse-proxy)
- [宝塔面板文档：SSL 证书](https://docs.bt.cn/user-guide/site/php/site-config/ssl)

---

## 第 1 步 从 GitHub 克隆代码

宝塔「终端」（或 SSH）执行：

```bash
git clone https://github.com/EthanYuan06/ai-tour-guide.git /www/wwwroot/ai-tour-guide
```

说明：

- 仓库若是私有，HTTPS 克隆会要求凭据——在 GitHub 上生成一个只读 fine-grained token 按提示粘贴即可；嫌麻烦可把仓库设为 public（仓库内不含任何密钥，`.env` 已被 .gitignore 排除）。
- GitHub 偶尔断流，重跑一次 `git clone` 即可续上；实在不稳定可在 Gitee 建镜像仓库后换成 Gitee 的 HTTPS 地址。

## 第 2 步 创建 .env 与 swap

1. 宝塔「文件」进入 `/www/wwwroot/ai-tour-guide`，新建 `.env`（该文件不进 Git 也不进镜像），内容 5 个 key，值只用字母数字和 `-` `_`（`$`、引号、空格会引发 compose 插值与 shell 转义问题）：

   ```ini
   DASHSCOPE_API_KEY=<你的百炼Key>
   AI_TOUR_GUIDE_CHAT_MODEL=deepseek-v4.1-flash
   MYSQL_USERNAME=root
   MYSQL_PASSWORD=<强密码A>
   MYSQL_ROOT_PASSWORD=<强密码A>
   ```

   注意：compose 里应用账号即 root，`MYSQL_PASSWORD` 与 `MYSQL_ROOT_PASSWORD` **必须相同**；本地 `.env` 的 `1234` 只用于本机开发，服务器上换强口令。

2. 2G 内存机器构建（`mvn package` / `npm ci`）有 OOM 风险，先加 swap：宝塔「监控」页 → 交换分区 → 设置 `2048MB`；或终端：

   ```bash
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab   # 开机自动挂载
   ```

3. Windows 端提交的 shell 脚本可能是 CRLF 换行，服务器上执行一次（仓库已加 `.gitattributes` 强制 LF，新 clone 一般无需此步）：

   ```bash
   cd /www/wwwroot/ai-tour-guide
   sed -i 's/\r$//' deploy.sh && chmod +x deploy.sh
   ```

## 第 3 步 构建并启动

```bash
cd /www/wwwroot/ai-tour-guide
./deploy.sh up        # 首次构建：拉基础镜像 + Maven/npm 下载依赖，预计 5~15 分钟
./deploy.sh status    # 等三个容器都变成 Up (healthy)
./deploy.sh logs backend | grep Started   # 确认 Spring 启动完成
```

预期节奏：mysql 首次初始化约 30~60s → healthy 后 backend 启动（约 40~90s）→ backend healthy 后 frontend 启动。`depends_on` 的健康条件保证了这个顺序。

## 第 4 步 宝塔建站 + 反向代理

出处：[添加网站](https://docs.bt.cn/user-guide/site/php/create-web)、[反向代理](https://docs.bt.cn/user-guide/site/php/site-config/reverse-proxy)。

1. 面板「网站」→「添加站点」：域名填 `yuluo-ai.site`（按需加 `www.yuluo-ai.site`）；PHP 版本选「纯静态」；不建 FTP；提交。站点根目录留默认即可，内容实际由容器提供。
2. 站点列表点域名 →「反向代理」→「添加反向代理」：
   - 代理名称：`app`
   - 目标 URL：`http://127.0.0.1:18080`
   - 发送域名：`$host`
   - 代理目录：**留空**（整站代理；`/api` 的分流由前端容器内 nginx 完成，宝塔层再配一遍会双层重复）
   - 缓存：勾选「关闭缓存」
   - 提交。注意：UI 的「关闭缓存」只关磁盘缓存，**不等于关 proxy_buffering**，SSE 还需要下一步。
3. 反向代理列表 →「配置文件」，在 `location /` 段**之前**插入 SSE 专用段并保存（面板会自动 reload）：

   ```nginx
   location /api/ {
       proxy_pass http://127.0.0.1:18080;
       proxy_http_version 1.1;
       proxy_set_header Connection "";
       proxy_set_header Host $host;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
       proxy_buffering off;
       proxy_read_timeout 600s;
       proxy_send_timeout 600s;
   }
   ```

   超时链必须是：宝塔 600s ≥ 前端容器 600s > Spring `request-timeout` 300s，任何一层倒挂都会表现为长回复中途断流。

## 第 5 步 HTTPS 证书与强制跳转

出处：[宝塔 SSL 证书](https://docs.bt.cn/user-guide/site/php/site-config/ssl)。

1. 站点 →「SSL」→「Let's Encrypt」：域名勾选 `yuluo-ai.site` 与 `www`（有几条解析就签几个），验证方式选「文件验证」→ 申请。成功后「证书有效期」会列出到期时间，宝塔自动续签。
2. 若签发失败提示 404/超时：多半是整站反代把 `/.well-known/acme-challenge` 验证请求也代理进了容器。临时处理：先「停用」反向代理 → 申请证书 → 恢复反向代理。
3. 打开「强制 HTTPS」开关。
4. `www` 与裸域并存时，在站点「域名管理」确认二者同根目录同反代，或加 301 统一到主域。

## 第 6 步 逐层验收（自底向上）

```bash
cd /www/wwwroot/ai-tour-guide

# 1. 编排层：三容器均 Up (healthy)
./deploy.sh status

# 2. 后端进程：健康检查
docker compose exec backend wget -qO- http://127.0.0.1:8123/api/health    # 期望 ok

# 3. 数据层：Spring AI 记忆表已自动建出（initialize-schema 生效）
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW TABLES IN ai_tour_guide;"'

# 4. 宿主机 → 前端容器 → 后端全链路（/api 前缀未被剥）
curl -s http://127.0.0.1:18080/api/health    # 期望 ok

# 5. 域名层
curl -sI http://yuluo-ai.site/ | head -3     # 期望 301 到 https
curl -s https://yuluo-ai.site/api/health     # 期望 ok

# 6. SSE 真流式：-N 关闭 curl 自身缓冲，data: 帧应分多次逐块出现；
#    一次性全部吐完 = 某一层 buffering 没关（见第 7 步排查）
curl -sN "https://yuluo-ai.site/api/chat/stream?message=%E4%BD%A0%E5%A5%BD"
```

浏览器终验：打开 `https://yuluo-ai.site` 发一轮对话 → F12 Network 里 `/api/chat/stream` 为 EventStream 且内容逐帧增长 → **刷新页面，会话列表和历史消息仍在**（MySQL 持久化）→ 服务器 `reboot` 后再验一次（数据卷 + 开机自启 `restart: unless-stopped` 生效）。

## 第 7 步 故障速查表

| 现象 | 排查顺序 |
|---|---|
| 域名整体打不开/超时 | 云防火墙是否仍放行 80/443 → 宝塔「安全」页 → 站点是否绑定域名 → A 记录是否指向本机 IP |
| 502 Bad Gateway | `./deploy.sh status` 容器没起 → `./deploy.sh logs backend` 看异常 → 端口被占（`ss -lntp \| grep -E '18080\|18123'`，宿主机手起的 Java 进程要先停） |
| 回复不逐字流式 | 三层缓冲逐个查：宝塔反代配置文件的 `location /api/` → 前端容器 nginx `proxy_buffering off` → 后端无额外层；改完宝塔须 reload |
| 长回复中途断流 | 超时链倒挂：宝塔/前端 600s 必须 ≥ Spring 300s；确认第 4 步的 `proxy_read_timeout` 片段已插入 |
| 后端连不上 MySQL | `SPRING_DATASOURCE_URL` 三个参数是否完整（丢 `allowPublicKeyRetrieval` 报 Public Key Retrieval）；库名是否小写 `ai_tour_guide`（Linux 大小写敏感）；`./deploy.sh status` 里 mysql 是否 healthy；`.env` 里两个密码是否一致 |
| 构建卡在 FROM 拉镜像 | Docker 镜像加速是否生效：`docker info` 查 Mirrors；兜底手动 pull + tag |
| 构建期进程被 Killed | swap 没加或太小；`docker compose build` 拆开逐服务构建；重试即可（层缓存会续上） |
| Let's Encrypt 签发失败 | 反代吞了 acme-challenge：暂停反代 → 签发 → 恢复（见第 5 步） |
| 误执行了 `docker compose down -v` | 数据卷被清。恢复只能靠备份 → 所以必须配置第 8 步的定时备份 |

## 第 8 步 日常运维

发版更新：

```bash
cd /www/wwwroot/ai-tour-guide
./deploy.sh update    # git pull --ff-only + 重建镜像 + 滚动 up -d
```

回滚：`git checkout <上个可用commit或tag> && ./deploy.sh up`。

看日志：`./deploy.sh logs backend`（或面板 Docker 管理器 → 容器 → 日志）。

数据库定时备份：面板「计划任务」→ Shell 脚本，每天 3:00 执行：

```bash
cd /www/wwwroot/ai-tour-guide
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" ai_tour_guide' \
  > /www/backup/sql/ai_tour_guide_$(date +%F).sql
```
