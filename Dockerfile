FROM java
MAINTAINER David Carboni

WORKDIR /uaa
ADD . /uaa

ENTRYPOINT ./gradlew run --info
# ENTRYPOINT ./gradlew run --info --stacktrace --debug
