FROM java
MAINTAINER David Carboni

WORKDIR /uaa
ADD . /uaa
ADD ./ras-config /ras-config

ENTRYPOINT ./gradlew run --info
# ENTRYPOINT ./gradlew run --info --stacktrace --debug
