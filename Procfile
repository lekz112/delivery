release: ./heroku-cloudamqp-url-to-spring-boot
web: java $JAVA_OPTS -XX:NativeMemoryTracking=summary -javaagent:dd-java-agent.jar -Dserver.port=$PORT -jar build/libs/*
