# chatbot
### Line 天氣預報機器人
- 取得氣象資料
- 整理後判斷關鍵字地區，透過Line回覆

### 使用教學
- 在內容打上地區會回覆您，該地區的天氣預報。

![](https://i.imgur.com/yXD4lbt.png)

- 還可以使用location，找到你所在的城市區域，進行天氣預報。

![](https://i.imgur.com/UXhUDzs.jpg)

### QR code
- 有興趣可以掃以下QR code 使用看看

![](https://i.imgur.com/pMhahKz.png)


### 版本
- Java 25
- Spring Boot 4.0.1 (Spring Framework 7.0.2)
- Gradle 9.4.1
- Lombok 1.18.38
- Jackson (jackson-datatype-jsr310)

### 技術特性
- **Virtual Threads** — `spring.threads.virtual.enabled: true`，高併發低資源
- **ZGC Generational** — `-XX:+UseZGC -XX:+ZGenerational`，低延遲 GC
- **Constructor Injection** — `@RequiredArgsConstructor` + `final` fields，全專案統一
- **Java Records** — DTO 類使用 record 簡化
- **Switch Expressions** — Java 25 語法
- **非同步啟動** — `CompletableFuture.runAsync()` 背景載入氣象資料，啟動約 3 秒
- **Thread Safety** — Entity 為純 POJO，singleton 無可變 instance field
- **Docker 多階段建置** — JDK 25 Alpine (build) → JRE 25 Alpine (runtime)，最小化 image

## 使用工具
| [Spring Boot](https://spring.io/projects/spring-boot) | [Line Messaging API](https://developers.line.biz/en/docs/messaging-api/overview/) | [MongoDB](https://www.mongodb.com/) | [Redis](https://redis.io/) |
| -------- | -------- | -------- | -------- |
| [Quartz Scheduler](https://docs.spring.io/spring-boot/reference/io/quartz.html) | [Docker](https://www.docker.com/) | [Render](https://render.com/) | [GCP Secret Manager](https://cloud.google.com/secret-manager) |

## 資料來源
- 政府openData [OpenData CWB](https://opendata.cwa.gov.tw/index) 
