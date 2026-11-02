FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /tmp/app.jar
# Unpack the jar into app.jar + lib/ so the JVM can use a class data sharing (CDS) archive
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/run && rm /tmp/app.jar
WORKDIR /app/run

# Startup flags for a small container (0.1 CPU, 512 MB):
#   TieredStopAtLevel=1  only the quick C1 compiler, so less CPU spent compiling while starting
#   UseSerialGC          simplest garbage collector, no extra GC threads on a single slow CPU
#   Xss512k              smaller thread stacks, less memory per thread
#   MaxRAMPercentage=75  heap may use up to 75% of the container memory
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k"

# Training run: start the app once during the build and save the loaded classes to app.jsa,
# so real startups read them ready-made instead of loading and verifying them again.
# No database is needed: the context stops right after it is built (spring.context.exit=onRefresh),
# Flyway is skipped and Hibernate is told not to read database metadata. The values below exist only for this run.
# If the training run ever fails, the build still succeeds and the app just starts without the archive.
RUN RESQORA_DB_URL=jdbc:postgresql://localhost:1/build-only \
    RESQORA_DB_USERNAME=build RESQORA_DB_PASSWORD=build \
    RESQORA_JWT_SECRET=build-only-training-secret-not-used-at-runtime \
    java -XX:ArchiveClassesAtExit=app.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -jar app.jar \
    || echo "CDS training run failed; the app will start without the archive"

EXPOSE 8080
# If app.jsa is missing or does not match, the JVM ignores it and starts normally
ENTRYPOINT ["java","-XX:SharedArchiveFile=app.jsa","-jar","app.jar"]
