# Multi-stage build for the headless TodoList jSpace server.
#
# The server is normally launched via its JavaFX GUI (dk.dtu.ServerApp, the jar's
# configured main class). For a container we launch dk.dtu.ServerMain instead: it
# starts ServerEngine directly with no JavaFX init, so it runs on a headless JRE.

# ---------------------------------------------------------------------------
# Build stage: install jSpace, build the app, stage server runtime deps.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# jSpace is not on Maven Central, so build and install it into the local
# repository first (mirrors .github/workflows/build-installers.yml).
RUN git clone https://github.com/pSpaces/jSpace.git \
    && mvn -B -f jSpace/pom.xml install -DskipTests

# Copy the reactor and build. Copy poms first so dependency resolution can be
# cached, then the sources.
COPY pom.xml ./
COPY shared/pom.xml shared/pom.xml
COPY server/pom.xml server/pom.xml
COPY client/pom.xml client/pom.xml
COPY shared/src shared/src
COPY server/src server/src
COPY client/src client/src

# Build all modules (install so shared is resolvable by server), skip tests.
RUN mvn -B clean install -DskipTests

# Stage the server runtime dependencies (jSpace, gson, shared jar, JavaFX jars)
# into a single directory so the runtime stage can put them all on the classpath.
RUN mvn -B -pl server dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/build/libs

# ---------------------------------------------------------------------------
# Runtime stage: headless JRE, no display, no JavaFX toolkit started.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Server application jar and its runtime dependencies (includes todolist-shared).
COPY --from=build /build/server/target/todolist-server-1.0.0.jar /app/
COPY --from=build /build/libs/ /app/

# Defaults for a hosted server. Bind on all interfaces INSIDE the container;
# the host publish (see docker-compose.yml) is what restricts real exposure.
ENV TODOLIST_BIND_HOST=0.0.0.0
ENV TODOLIST_PORT=9001
ENV TODOLIST_DATA_DIR=/data

# session.json is persisted here; keep it on a volume so it survives restarts.
VOLUME /data

# jSpace raw TCP gate.
EXPOSE 9001

# Launch the headless entry point explicitly by class name (NOT the jar's
# configured GUI main class dk.dtu.ServerApp).
#
# Config reads the bind host and port from TODOLIST_BIND_HOST / TODOLIST_PORT
# env vars, but the data directory is read ONLY from the todolist.data.dir
# system property (Config.getDataDirectory has no env fallback). So we translate
# TODOLIST_DATA_DIR into that system property here. exec keeps java as PID 1 so
# the shutdown hook (which saves session.json) runs on stop.
ENTRYPOINT ["sh", "-c", "exec java -Dtodolist.data.dir=\"$TODOLIST_DATA_DIR\" -cp \"/app/*\" dk.dtu.ServerMain"]
