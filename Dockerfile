# ---------- 阶段1：构建 Vue3 前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json ./
RUN npm install --registry https://registry.npmmirror.com
COPY frontend/ ./
RUN npm run build

# ---------- 阶段2：Maven 构建 Spring Boot 后端（前端产物打进 static） ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/settings.xml /root/.m2/settings.xml
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline -q || true
COPY backend/src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -B clean package -DskipTests -q && mv target/night-pharmacy.jar /app.jar

# ---------- 阶段3：精简 JRE 运行（非 root + 健康检查） ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
RUN useradd -r -u 1001 -m -d /home/night night \
    && mkdir -p /app/data/uploads \
    && chown -R night:night /app
USER night
WORKDIR /app
COPY --from=backend --chown=night:night /app.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"success\":true" <&3' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
