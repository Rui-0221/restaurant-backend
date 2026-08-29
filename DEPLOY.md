# 公网部署指南（面试演示用）

同源反代架构：前后端同域名，Nginx 一个入口，`/api` 和 `/ws` 反代到后端 8080。
前端用相对路径 `/api`（axios `baseURL`），**无需 CORS 配置**，与开发环境 Vite proxy 行为一致。

```
浏览器 ──> http://IP  （顾客 H5，dist 静态文件）
      └─> http://IP/admin （员工端 + 后厨屏）
      └─> http://IP/api/*  ── Nginx ──> http://127.0.0.1:8080/*（剥掉 /api）
      └─> ws://IP/ws/kitchen ── Nginx ──> ws://127.0.0.1:8080/ws/kitchen（带 Upgrade 头）
```

前提：2C2G 服务器（Ubuntu 22.04，固定带宽 3M），安全组已开 **22 / 80 / 443**。

---

## 第 1 步（本机）：改 3 处前端代码 + 重新构建

### 1.1 三处代码改动（已适配公网，本地开发不受影响）

**① `restaurant-frontend/admin/src/views/Kitchen.vue` 第 110 行** — WebSocket 地址分环境：开发直连 8080（原样），生产跟随当前域名（同源反代）：

```js
// 原来：ws = new WebSocket(`ws://${location.hostname}:8080/ws/kitchen?token=${token}`)
const proto = location.protocol === 'https:' ? 'wss' : 'ws'
ws = new WebSocket(import.meta.env.DEV
  ? `ws://${location.hostname}:8080/ws/kitchen?token=${token}`   // 开发：直连后端（同原代码）
  : `${proto}://${location.host}/ws/kitchen?token=${token}`     // 生产：同源走 Nginx /ws 反代
)
```

> 不能直接换成 `location.host`——开发时页面在 `localhost:5174`，而 Vite 只代理 `/api` 不代理 `/ws`，后厨屏实时推送会断。

**② `restaurant-frontend/admin/src/views/Tables.vue` 第 175 行** — 二维码分环境：开发仍指向顾客端 5173（原样），生产指向当前站点根路径（顾客端所在）：

```js
// 原来：qrUrl.value = `${location.protocol}//${host}:5173/#/table/${row.id}`
const customerBase = import.meta.env.DEV ? `${location.protocol}//${host}:5173` : location.origin
qrUrl.value = `${customerBase}/#/table/${row.id}`
```

> 不能直接换成 `location.origin`——开发时管理端在 5174，二维码会落在管理端而路由不存在；`host` 变量继续被使用，不留孤儿代码。

**③ `restaurant-frontend/admin/package.json`** — 员工端部署在 `/admin/` 子路径，只在**构建时**加 base，开发时 `npm run dev` 完全不变：

```json
"scripts": {
  "build": "vite build --base=/admin/"
}
```

> 不放进 `vite.config.js` 是因为 `base` 会影响 dev server 路径（开发地址会变成 `localhost:5174/admin/`）；写在 build 脚本里只作用于构建产物。

（顾客端部署在根路径，`base` 保持默认 `/` 不用动。）

### 1.2 构建三个产物

```powershell
# 将下方路径替换为两个仓库共同的父目录
$restaurantWorkspace = "D:\path\to\restaurant-project"

# 顾客端
Set-Location "$restaurantWorkspace\restaurant-frontend\customer"
npm run build

# 员工端
Set-Location "$restaurantWorkspace\restaurant-frontend\admin"
npm run build

# 后端 jar
Set-Location "$restaurantWorkspace\restaurant-backend"
.\mvnw.cmd package -DskipTests
```

产物：`customer/dist/`、`admin/dist/`、`target/restaurant-backend-0.0.1-SNAPSHOT.jar`

---

## 第 2 步：SSH 连服务器 + 装环境

```bash
ssh root@<公网IP>
```

```bash
# 换源（可选，国内服务器 apt 快很多）
sed -i 's|archive.ubuntu.com|mirrors.aliyun.com|g' /etc/apt/sources.list && apt update

apt update && apt install -y openjdk-17-jdk nginx mysql-server redis-server
java -version   # 确认 17

# 1G swap 兜底（2G 内存必做）
fallocate -l 1G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h         # 应显示 Swap 1.0G
```

---

## 第 3 步：初始化 MySQL + Redis

```bash
systemctl start mysql redis-server
systemctl enable mysql redis-server nginx
```

```bash
# MySQL 8 的 root 默认 auth_socket 认证，sudo 直进
mysql -u root <<'EOF'
CREATE DATABASE restaurant_management DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'restaurant'@'localhost' IDENTIFIED BY '<换成强密码>';
GRANT ALL PRIVILEGES ON restaurant_management.* TO 'restaurant'@'localhost';
FLUSH PRIVILEGES;
EOF
```

```bash
# 导入表结构（init.sql 在 jar 包内，解出后导入；漏了这步接口会报"数据库操作失败"）
cd /opt/restaurant && jar xf app.jar BOOT-INF/classes/db/init.sql
mysql -u restaurant -p<换成强密码> restaurant_management < BOOT-INF/classes/db/init.sql
```

> `init.sql` 会删除并重建表，只能用于全新环境。已有数据库升级时禁止执行它；应从 jar 解出 `BOOT-INF/classes/db/migration/`，按文件名顺序执行增量脚本，AI 点餐至少需要 `20260827_01_ai_ordering_profile.sql` 和 `20260827_02_ai_order_submission.sql`。

```bash
# 已有数据库：增量脚本可重复执行；显式指定 utf8mb4，且手册种子只补缺失记录
mysql --default-character-set=utf8mb4 -u restaurant -p restaurant_management < BOOT-INF/classes/db/migration/20260827_01_ai_ordering_profile.sql
mysql --default-character-set=utf8mb4 -u restaurant -p restaurant_management < BOOT-INF/classes/db/migration/20260827_02_ai_order_submission.sql
```

```bash
# Redis 设密码（配置后 app 用 REDIS_PASSWORD 连）
sed -i 's|^# requirepass foobared|requirepass <换成强密码>|' /etc/redis/redis.conf
systemctl restart redis-server
```

---

## 第 4 步：上传产物

```powershell
# 本机 PowerShell 执行，<公网IP> 换成实际 IP
ssh root@<公网IP> "mkdir -p /opt/restaurant /var/www/customer /var/www/admin"

scp target\restaurant-backend-0.0.1-SNAPSHOT.jar root@<公网IP>:/opt/restaurant/app.jar
scp -r ..\restaurant-frontend\customer\dist\* root@<公网IP>:/var/www/customer/
scp -r ..\restaurant-frontend\admin\dist\* root@<公网IP>:/var/www/admin/
```

> ⚠️ scp 必须加 `-r`（dist 里有 assets 子目录，不加会漏传导致页面空白/404）。
> 若页面 403（nginx 以 www-data 身份读不了 Windows 传上去的文件），执行 `chmod -R a+rX /var/www/customer /var/www/admin`。

---

## 第 5 步：配置后端 systemd 守护

```bash
# 服务器上执行
cat > /etc/systemd/system/restaurant.service <<'EOF'
[Unit]
Description=Restaurant Backend
After=network.target mysql.service redis-server.service

[Service]
WorkingDirectory=/opt/restaurant
Environment="DB_URL=jdbc:mysql://localhost:3306/restaurant_management?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8"
Environment="DB_USERNAME=restaurant"
Environment="DB_PASSWORD=<和第3步一致>"
Environment="REDIS_PASSWORD=<和第3步一致>"
Environment="JWT_SECRET=<至少32位随机字符串>"
Environment="DEEPSEEK_ENABLED=true"
Environment="DEEPSEEK_API_KEY=<DeepSeek API Key>"
# 可选：Environment="DEEPSEEK_MODEL=deepseek-v4-flash"
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/restaurant/app.jar --spring.profiles.active=prod
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

```bash
systemctl daemon-reload
systemctl enable --now restaurant
systemctl status restaurant          # active (running)
curl -s http://127.0.0.1:8080/categories | head -c 300   # 有 JSON 返回即通
```

> ⚠️ **必须加 `--spring.profiles.active=prod`**：`application.yml` 默认激活 `local` profile，
> 而 jar 里打包了 `application-local.yml`（把数据库用户名写死成 root，本机开发用的），
> profile 文件优先级高于基础文件，会**覆盖环境变量**导致连不上数据库（经典坑）。
> 切到 `prod`（服务器上没有 prod 文件）后只加载基础 `application.yml`，
> `${DB_USERNAME:root}` 等占位符才从环境变量取值；`-Xmx512m` 限堆让 2G 内存三进程共存。

---

## 第 6 步：配置 Nginx

```bash
cat > /etc/nginx/sites-available/restaurant <<'EOF'
server {
    listen 80;
    server_name _;

    # 顾客 H5 → 根路径（hash 路由，无需 fallback）
    root /var/www/customer;
    index index.html;

    # /admin 不带斜杠时跳转到 /admin/（否则会落到根目录 404）
    location = /admin {
        return 301 /admin/;
    }

    # 员工端 + 后厨屏 → /admin/
    location /admin/ {
        alias /var/www/admin/;
        index index.html;
    }

    # API → 后端（location 和 proxy_pass 都必须带斜杠才能剥掉 /api 前缀；
    # 只给 proxy_pass 加斜杠会拼出 //categories 双斜杠，Spring 白名单不匹配）
    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket（后厨屏）→ 后端，必须带 Upgrade 头
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
EOF

rm /etc/nginx/sites-enabled/default
ln -s /etc/nginx/sites-available/restaurant /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

---

## 第 7 步：验证全流程

浏览器访问（注意安全组已开 80，8080 不开）：

| 入口 | 地址 | 验证 |
|---|---|---|
| 顾客端 | `http://<公网IP>/` | 菜单加载（走 `/api` 反代 + Redis 缓存） |
| 员工端 | `http://<公网IP>/admin/` | admin / 123456 登录 |
| 桌台管理 | 员工端 → 桌台管理 | 生成二维码 → 手机扫出 `http://<公网IP>/#/table/1` |
| 后厨屏 | 员工端 → 后厨屏（chef / 123456 登录） | WS 连接成功；顾客下单 → 实时播报 |
| 全链路 | 手机下单 → 后厨屏新订单 → 服务员端接单 | 状态流转 1→2→3→4→5 |

`systemctl status restaurant` 输出 `active (running)`，`journalctl -u restaurant -f` 可看日志。

---

## 可选优化（面试加分，不是必须）

- **HTTPS**：有域名后 `apt install -y certbot` 签发免费证书（或阿里云免费证书）。
  前端代码已自动适配（`location.protocol === 'https:'` 时 WS 自动走 wss），Nginx 加 443 配置即可。
- **域名 + ICP 备案**：大陆服务器绑域名需备案（1~2 周）；不想备案就继续用 IP 访问，或买香港节点。
- **JWT_SECRET / DB 密码**：演示项目写死环境变量可接受；生产应从 KMS/配置中心取。

## 排错速查

- `curl -s http://127.0.0.1:8080/categories` 无返回 → 看 `journalctl -u restaurant -n 50`（多半是 DB/Redis 连不上）
- 接口报「缺少token」/ 404 → `/api` 的 `location` 和 `proxy_pass` 都要带尾部 `/`（只给 `proxy_pass` 加会拼出 `//categories` 双斜杠，后端白名单不匹配）（剥前缀全靠它）
- 后厨屏连不上 WS → 确认 `/ws` 的 Upgrade 头没丢、8080 没直连
- 手机扫不出码 → 浏览器走的是 `http://IP` 而非 localhost；Tables.vue 的 localhost 拦截提示属正常
