FROM amazoncorretto:11.0.21-alpine

EXPOSE 9911

VOLUME /etc/sns

ENV DB_PATH=/etc/sns/db.json \
    DB_OUTPUT_PATH=/etc/sns/db.json

ARG INCLUDE_AWS_CLI=true

RUN apk update \
    && if [ "$INCLUDE_AWS_CLI" = "true" ]; then apk add --no-cache aws-cli; fi \
    && apk cache clean

ARG JAR=undefined
ADD $JAR /local-sns.jar

CMD ["java", "-jar", "/local-sns.jar"]