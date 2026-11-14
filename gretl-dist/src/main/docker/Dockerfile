# Verified 2026-11-01: Java 25.0.5, Ubuntu 24.04; index includes amd64 and arm64.
FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
COPY dist/ /opt/gretl/
RUN find /opt/gretl -type d -exec chmod 755 {} + \
    && find /opt/gretl -type f -exec chmod 644 {} + \
    && chmod 755 /opt/gretl/bin/gretl
# OpenShift may replace the UID. Only the application data directories need group write.
RUN mkdir -p /work/home /work/tmp /reports /outputs /run \
    && chgrp -R 0 /work /reports /outputs \
    && chmod -R g=u /work /reports /outputs \
    && chmod g+rwx /run
ENV HOME=/work TMPDIR=/tmp LANG=C.UTF-8 TZ=UTC \
    JAVA_TOOL_OPTIONS="-Duser.home=/work -Djava.io.tmpdir=/tmp"
WORKDIR /run
USER 10001:0
ENTRYPOINT ["/opt/gretl/bin/gretl"]
