DB_PATH := ./db.json

TAG = dev

run-dev:
	DB_PATH=$(DB_PATH) ./gradlew run

build:
	./gradlew clean build

build-image:
	./gradlew clean assemble
	docker build --build-arg JAR=$(JAR) --tag jameskbride/local-sns:$(TAG) .

publish: build
	docker buildx build --platform=linux/arm64,linux/amd64 --build-arg JAR=$(JAR) -t jameskbride/local-sns:$(TAG) -f Dockerfile . --push