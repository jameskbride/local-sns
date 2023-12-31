# See https://github.com/s12v/sns/issues/60
FROM amazoncorretto:11.0.21-alpine

EXPOSE 9911

VOLUME /etc/sns

ENV AWS_DEFAULT_REGION=us-east-1 \
	AWS_ACCESS_KEY_ID=foo \
	AWS_SECRET_ACCESS_KEY=bar \
	AWS_ACCOUNT_ID=123456789012 \
	DB_PATH=/etc/sns/db.json \
	DB_OUTPUT_PATH=/etc/sns/db.json

RUN apk update \
    && apk add --no-cache aws-cli \
    && apk cache clean

ARG JAR=undefined
ADD $JAR /local-sns.jar

CMD ["java", "-jar", "/local-sns.jar"]
