# 多阶段构建：前端(Vite) → 后端(Maven) → 运行(JRE)
# 构建上下文为仓库根目录：docker build -t court-mediation .

# ---- 1. 前端构建（产物输出到 backend/src/main/resources/static） ----
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY frontend ./frontend
RUN mkdir -p backend/src/main/resources && cd frontend && npm run build

# ---- 2. 后端打包（含上一步前端静态资源，产出单 jar） ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend ./backend
# 清掉仓库自带的旧前端产物，统一使用上一阶段最新构建，避免 hash 文件混杂
RUN rm -rf ./backend/src/main/resources/static
COPY --from=frontend /app/backend/src/main/resources/static ./backend/src/main/resources/static
RUN cd backend && mvn -B clean package -DskipTests

# ---- 3. 运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/target/mediation-platform.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
