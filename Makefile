JAR = build/libs/local-sns-1.0.0.jar

TAG = dev

build-image:
	./gradlew clean assemble
	docker build --build-arg JAR=$(JAR) --tag jameskbride/local-sns:$(TAG) .