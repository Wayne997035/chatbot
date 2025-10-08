# ---------- Stage 1: Build ----------
FROM gradle:7.6.6-jdk17-alpine AS build

WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

# build，跳過測試，加快速度
RUN gradle clean build -x test --no-daemon --console=plain


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

# 設定時區與 JVM 參數
ENV TZ=Asia/Taipei \
    JAVA_OPTS="-server \
               -XX:+UseG1GC \
               -Xms128m -Xmx384m \
               -XX:MaxMetaspaceSize=128m \
               -XX:+UseStringDeduplication \
               -XX:+AlwaysPreTouch \
               -Dfile.encoding=UTF-8"

WORKDIR /app

# 拷貝 build 的 JAR
COPY --from=build /home/gradle/src/build/libs/*-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# entrypoint 保留靈活性，可注入 JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]