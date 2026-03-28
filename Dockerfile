# ---------- Stage 1: Build ----------
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# 先 copy gradle 相關檔案（Docker cache friendly）
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 再 copy source code
COPY src/ src/
RUN ./gradlew clean build -x test --no-daemon --console=plain


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

# 設定時區與 JVM 參數
ENV TZ=Asia/Taipei \
    JAVA_OPTS="-server \
               -XX:+UseZGC -XX:+ZGenerational \
               -Xms128m -Xmx384m \
               -XX:MaxMetaspaceSize=128m \
               -XX:+UseStringDeduplication \
               -XX:+AlwaysPreTouch \
               -Dfile.encoding=UTF-8"

WORKDIR /app

# 拷貝 build 的 JAR
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# entrypoint 保留靈活性，可注入 JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
