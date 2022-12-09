javac HelloWorld/*.java && \
jar cf HelloWorld.jar HelloWorld/*.class && \
javac Bootstrapper/Main.java && \
jar cfe Bootstrapper.jar Bootstrapper.Main Bootstrapper/Main.class && \
cp HelloWorld.jar OrigHelloWorld.jar && \
java -jar Bootstrapper.jar;
