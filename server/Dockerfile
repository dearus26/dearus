# Dear Us 서버 이미지 (Render 배포용, 2026.11.18)
# 1단계에서 Gradle로 실행 파일(jar)을 만들고, 2단계에서는 JRE만 있는 작은 이미지로 실행해요.
# 비밀 값(DB 비밀번호 · 키)은 이미지에 넣지 않고, Render 환경변수로 실행할 때 받아요.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 라이브러리 jar를 소스보다 먼저 받아 두면, 코드만 바뀌었을 때는 이 층을 캐시에서 다시 써서 Maven Central에 가지 않아요.
# (예전의 `dependencies` 작업은 목록만 보고 jar를 받지 않아서, 배포마다 jar를 새로 받다가 429로 실패했어요, 2026.11.28)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon downloadDependencies
COPY src src
# 테스트는 각자 PC에서 돌려요(DB · AI 키가 필요한 시험은 기본으로 건너뛰어요)
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 dearus
COPY --from=build /workspace/app.jar app.jar
USER dearus
# Render 무료 요금제는 메모리 512MB · CPU 0.1개라서 힙을 절반(약 256MB)으로 묶고, 작은 GC와 가벼운 컴파일(C1)만 써요.
# 2026.11.18 로컬에서 같은 설정(-Xmx256m)으로 켜 보니 프로세스 전체가 약 350MB였어요
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=160m -XX:TieredStopAtLevel=1"
# Render가 PORT 환경변수를 넘겨줘요(application.yml의 server.port). 없으면 8080이에요
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
