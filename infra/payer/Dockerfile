FROM hapiproject/hapi:v7.4.0 AS build
COPY HealthCheck.java /tmp/HealthCheck.java
RUN ["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", "/tmp", "/tmp/HealthCheck.java"]

FROM hapiproject/hapi:v8.12.0-2
COPY --from=build /tmp/HealthCheck.class /healthcheck/HealthCheck.class
