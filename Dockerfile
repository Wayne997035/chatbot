# ---------- Stage 1: Build ----------
FROM gradle:7.6.6-jdk17-alpine AS build

WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

# build
RUN gradle clean build -x test --no-daemon --no-parallel --console=plain


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

# 設定時區與 JVM 參數
ENV TZ=Asia/Taipei \
    JAVA_OPTS="-server -XX:+UseG1GC -Xlog:gc* \
               -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=85 \
               -XX:MinRAMPercentage=40 -XX:+UseStringDeduplication"

WORKDIR /app

# build JAR
COPY --from=build /home/gradle/src/build/libs/*-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# entrypoint：保持靈活，可注入 JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
