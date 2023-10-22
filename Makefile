JAR = build/libs/fake-sns.jar

TAG = dev

build-image:
	./gradlew clean assemble
	docker build --build-arg JAR=$(JAR) --tag fake-sns:$(TAG) .