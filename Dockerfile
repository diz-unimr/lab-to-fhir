FROM gradle:6.6-jdk11 AS build
WORKDIR /home/gradle/src
ENV GRADLE_USER_HOME /gradle

COPY build.gradle settings.gradle ./
RUN gradle build || true

COPY --chown=gradle:gradle . .
RUN gradle build -x integrationTest --info && \
    gradle jacocoTestReport && \
    awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' build/jacoco/coverage.csv && \
    java -Djarmode=layertools -jar build/libs/*.jar extract

FROM gcr.io/distroless/java:11
COPY cert/RKA_Root_CA_2.cer /tmp/RKA_Root_CA_2.cer
RUN [\
 "/usr/lib/jvm/java-11-openjdk-amd64/bin/keytool",\
 "-import",\
 "-trustcacerts",\
 "-cacerts",\
 "-noprompt",\
 "-storepass",\
 "changeit",\
 "-alias",\
 "rka_root_ca_2",\
 "-file",\
 "/tmp/RKA_Root_CA_2.cer"\
]
WORKDIR /opt/lab-to-fhir
COPY --from=build /home/gradle/src/dependencies/ ./
COPY --from=build /home/gradle/src/spring-boot-loader/ ./
COPY --from=build /home/gradle/src/application/ ./
COPY HealthCheck.java .

USER nonroot
ARG GIT_REF=""
ARG GIT_URL=""
ARG BUILD_TIME=""
ARG VERSION=0.0.0
ENV APP_VERSION=${VERSION} \
    SPRING_PROFILES_ACTIVE="prod"
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90", "org.springframework.boot.loader.JarLauncher"]

HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD ["java", "HealthCheck.java", "||", "exit", "1"]

LABEL org.opencontainers.image.created=${BUILD_TIME} \
    org.opencontainers.image.authors="Sebastian Stöcker" \
    org.opencontainers.image.source=${GIT_URL} \
    org.opencontainers.image.version=${VERSION} \
    org.opencontainers.image.revision=${GIT_REF} \
    org.opencontainers.image.vendor="diz.uni-marburg.de" \
    org.opencontainers.image.title="lab-to-fhir" \
    org.opencontainers.image.description="Kafka Streams processor converting laboratory data fo FHIR."
