# CS6650DistributedSystem


## How to Run the Client

### 1. Update the Server URL
- Open the `Consumer.java` file in:
    - `assignment1_p1`
    - `assignment1_p2`
- Find this line and change it if needed:
  ```java
  public static final String BASE_URL = "http://publicIPAddress:8080/Assignment/";

### 2. Run the Client

1. Navigate to:
    - `assignment1_p1` → Run `SendRequests.java`
    - `assignment1_p2` → Run `SendRequests.java`
2. Click **Run** to execute.


## How to Run the Server

### 1. Update the HOST URL
- Open the `SkierServlet.java`
- Find this line and change it if needed:
  mvn clean install


### 2. Upload the war file to ec2 instance
- The `Server.war` is located in `Server/out/Server.war`

### 3. Start the Tomcat in ec2


## How to Run the Consumer
### 1. Update the HOST URL
- Open the `MultiThreadedConsumer.java`
- Find this line and change it if needed:
  ```java
  private static final String HOST = "172.31.31.xxx";

### 2. Upload the jar file to Consumer ec2 instance
- Run
  ```java
   mvn clean install

- Upload `Consumer/target/Consumer-1.0-SNAPSHOT-jar-with-dependencies.jar` to the ec2

### 3. Run jar file in ec2 instance
  ```java
   java -jar Consumer-1.0-SNAPSHOT-jar-with-dependencies.jar
