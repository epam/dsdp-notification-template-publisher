FROM amazoncorretto:11-al2023

#hadolint ignore=DL3041
RUN dnf update -y --security \
    && dnf clean all \
    && rm -rf /var/cache/dnf
    
WORKDIR /app
COPY target/notification-template-publisher*.jar app.jar