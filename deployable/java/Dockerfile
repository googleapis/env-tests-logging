# compile local java-logging library
FROM docker.io/maven AS lib-env
WORKDIR /app
COPY java-logging ./
RUN mvn verify --fail-never
RUN mvn -Dmaven.test.skip=true package

# Compile the deployable code.
FROM docker.io/maven AS build-env
WORKDIR /app
COPY pom.xml /app/pom.xml
# copy over compiled library
COPY --from=lib-env /app/target/*.jar /app/java-logging.jar
# install java-logging
RUN mvn install:install-file \
    -Dfile=/app/java-logging.jar \
    -DgroupId=com.google.cloud.local \
    -DartifactId=google-cloud-logging \
    -Dversion=0.0.1 \
    -Dpackaging=jar \
    -DgeneratePom=true
# download dependencies as specified in pom.xml
RUN mvn verify --fail-never
COPY src /app/src
RUN mvn -Dmaven.test.skip=true package

# Build runtime image.
FROM openjdk:8-jre-slim
# Copy the compiled files over.
COPY --from=build-env /app/target/ /app/
ENV PORT=8080
# Starts libapp with debugging server at port 5005.
CMD ["java", "-jar", "/app/deployable-1.0.0.jar"]
