# ---- 阶段1：构建前端（产物输出到 backend/src/main/resources/static）----
FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
# 仅复制依赖清单，利用层缓存
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
# vite.outDir 指向 ../backend/src/main/resources/static，预先创建目录
RUN mkdir -p /build/backend/src/main/resources/static \
    && npm run build

# ---- 阶段2：构建后端（将前端产物并入 static，单 jar 提供页面+API）----
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/pom.xml ./pom.xml
# 预热依赖（个别插件元数据缺失不阻断，正式 package 会补齐）
RUN mvn -B -q dependency:go-offline || true
COPY backend/src ./src
# 用阶段1的前端构建产物覆盖 static
RUN rm -rf src/main/resources/static \
    && mkdir -p src/main/resources/static
COPY --from=frontend /build/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q clean package -DskipTests

# ---- 阶段3：运行 ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=backend /build/target/mediation-platform.jar /app/mediation-platform.jar
# 附件（付款凭证/异议材料）持久化目录
RUN mkdir -p /app/data/attachments
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/mediation-platform.jar"]
