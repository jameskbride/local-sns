JAR = build/libs/local-sns-0.9.0.jar

TAG = dev

build-image:
	./gradlew clean assemble
	docker build --build-arg JAR=$(JAR) --tag jameskbride/local-sns:$(TAG) .