#!/bin/sh
# ai-tour-guide 部署入口
#   ./deploy.sh up                      构建并后台启动全部服务
#   ./deploy.sh down                    停止并移除容器（保留数据卷）
#   ./deploy.sh restart                 重启全部服务
#   ./deploy.sh update                  拉取代码、重建镜像并滚动更新
#   ./deploy.sh status                  查看容器状态
#   ./deploy.sh logs [mysql|backend|frontend]  跟随日志（默认全部）
set -e
cd "$(dirname "$0")"

if docker compose version >/dev/null 2>&1; then
    DC="docker compose"
else
    DC="docker-compose"
fi

case "$1" in
    up)
        $DC up -d --build
        ;;
    down)
        # 刻意不带 -v：数据卷（MySQL）永久保留
        $DC down
        ;;
    restart)
        $DC restart
        ;;
    update)
        git pull --ff-only
        $DC build
        $DC up -d
        ;;
    status)
        $DC ps
        ;;
    logs)
        $DC logs -f --tail=200 $2
        ;;
    *)
        echo "用法: $0 {up|down|restart|update|status|logs [服务名]}"
        exit 1
        ;;
esac
