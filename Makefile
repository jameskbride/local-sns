DB_PATH := ./db.json

TAG = dev

JAR ?= $(wildcard build/libs/local-sns-*.jar)

run-dev:
	DB_PATH=$(DB_PATH) ./gradlew run

assemble:
	./gradlew clean assemble

build-image: assemble
	docker build --build-arg JAR=$(JAR) --tag jameskbride/local-sns:$(TAG) .

publish: assemble
	docker buildx build --platform=linux/arm64,linux/amd64 --build-arg JAR=$(JAR) -t jameskbride/local-sns:$(TAG) -f Dockerfile . --push