# See https://github.com/s12v/sns/issues/60
FROM openjdk:11.0.13-jre-slim

EXPOSE 9911

VOLUME /etc/sns

ENV AWS_DEFAULT_REGION=us-east-1 \
	AWS_ACCESS_KEY_ID=foo \
	AWS_SECRET_ACCESS_KEY=bar \
	AWS_ACCOUNT_ID=123456789012 \
	DB_PATH=/etc/sns/db.json \
	DB_OUTPUT_PATH=/etc/sns/db.json

# Install the AWS CLI using apt
RUN apt-get update && \
    apt-get install -y awscli && \
    apt-get clean

ARG JAR=undefined
ADD $JAR /local-sns.jar

CMD ["java", "-jar", "/local-sns.jar"]
