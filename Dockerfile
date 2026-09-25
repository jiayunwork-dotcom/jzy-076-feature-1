# syntax=docker/dockerfile:1

# ===== 构建阶段：带 JDK 17 的 Maven 官方镜像编译可执行包 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先单独拷贝 pom，利用依赖缓存层
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 拷贝源码并打包（含自动化测试，任何一条判据不通过都不产出镜像）
COPY src ./src
RUN mvn -B clean package

# ===== 运行阶段：瘦身 JRE 17 镜像只加载可执行包 =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 仅拷贝构建产物，镜像内不带 Maven、不带 JDK、不带源码
COPY --from=build /build/target/distillation-shortcut-service-1.0.0.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
