# syntax=docker/dockerfile:1

# ============ 第一阶段：构建 ============
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# 使用国内 Maven 镜像（阿里云）加速依赖下载
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 复制源码并打包（依赖在打包时从阿里云镜像下载）
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ============ 第二阶段：运行 ============
FROM eclipse-temurin:25-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -Dfile.encoding=UTF-8"

COPY --from=build /build/target/*.jar app.jar

# 用非 root 用户运行
RUN useradd -m medislot && chown -R medislot /app
USER medislot

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
