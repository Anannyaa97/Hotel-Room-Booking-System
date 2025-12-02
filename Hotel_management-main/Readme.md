## step1 
javac -cp src/lib/mysql-connector-j-9.4.0.jar -d out $(find src -name "*.java")
## step 2
java -cp out:src/lib/mysql-connector-j-9.4.0.jar Main
## step 3
Owner
username = owner
password = ownerpass
