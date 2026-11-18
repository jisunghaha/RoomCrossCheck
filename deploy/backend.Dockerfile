# 운영 배포용 백엔드 이미지: 서버에서 JAR을 빌드해 Java 21 JRE 이미지로 실행한다.
# 테스트는 배포 전에 로컬이나 CI에서 실행한다. 작은 VM의 메모리와 빌드 시간을 줄이기 위해 여기서는 생략한다.
# 빌드 컨텍스트는 backend 폴더이다.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
