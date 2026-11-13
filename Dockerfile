# 运行时镜像覆盖口：默认 alpine（自带 busybox wget 供 HEALTHCHECK 用）；
# 全局 ARG 必须放在第一个 FROM 之前才能被 FROM 引用
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

### 阶段一：Maven 构建 fat jar ###
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 阿里云中央仓库镜像，加速内地服务器构建；
# 注意不能放 /root/.m2/settings.xml——该目录被下方的 cache mount 遮蔽，必须用 -s 显式指定
COPY docker/maven-settings.xml /root/settings.xml

# 依赖与源码分层：只改代码时不重复解析依赖；--mount=type=cache 跨构建复用 ~/.m2
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /root/settings.xml dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /root/settings.xml package -DskipTests

### 阶段二：JRE 运行时（alpine 版自带 busybox wget，供 HEALTHCHECK 使用） ###
FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
# 通配 jar 名，规避版本号升级断链
COPY --from=builder --chown=app:app /build/target/*.jar app.jar
USER app

EXPOSE 8123
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8123/api/health | grep -q ok || exit 1

# JVM 内存参数不放镜像里，由 docker-compose 的 JAVA_TOOL_OPTIONS 注入，与部署环境解耦
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
