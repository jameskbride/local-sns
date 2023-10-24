JAR = build/libs/fake-sns-1.0.0.jar

TAG = dev

build-image:
	./gradlew clean assemble
	docker build --build-arg JAR=$(JAR) --tag fake-sns:$(TAG) .