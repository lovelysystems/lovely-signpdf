FROM openjdk:8u151-jre-alpine
COPY *.tar /install/
RUN mkdir app && cd app && tar --strip-components=1 -xf /install/*.tar && rm -rf /install
ENTRYPOINT /app/bin/lovely-signpdf
