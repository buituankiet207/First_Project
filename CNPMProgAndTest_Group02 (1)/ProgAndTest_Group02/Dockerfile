# ---- build stage: compiles the code and builds one runnable jar (tests are run separately, see README)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 lms
COPY --from=build /app/target/library.jar /app/library.jar
USER lms
ENV PORT=8080 STORAGE=mysql
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/library.jar"]
