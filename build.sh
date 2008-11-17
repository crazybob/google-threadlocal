rm -r out
mkdir out
mkdir out/test
mkdir out/main

javac -d out/main -g main/java/lang/*.java
javac -cp lib/junit.jar -d out/test -g test/*.java test/java/lang/*.java

