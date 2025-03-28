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
### 1. Update the HOST URL and Database config
- Open the `MultiThreadedConsumer.java`
- Find this line and change it if needed:
  ```java
  private static final String HOST = "172.31.31.xxx";
- Find this line and change the region if needed:
  ```java
  DynamoDbClient dynamoDb = DynamoDbClient.builder()
        .region(Region.US_WEST_2)
        .build();

### 2. Upload the jar file to Consumer ec2 instance
- Run
  ```java
   mvn clean install

- Upload `Consumer/target/Consumer-1.0-SNAPSHOT-jar-with-dependencies.jar` to the ec2

### 3. Run jar file in ec2 instance
- Run
  ```java
   java -jar Consumer-1.0-SNAPSHOT-jar-with-dependencies.jar


## How to create the DynamoDB table
### 1. set up the aws credential
- Run and set up the credentials
    ```angular2svg
        nano ~/.aws/credentials
### 2. Create table
 ```angular2svg
    aws dynamodb create-table \
      --table-name lift_rides \
      --attribute-definitions \
        AttributeName=SkierID,AttributeType=N \
        AttributeName=SeasonDayTime,AttributeType=S \
        AttributeName=ResortSeasonDayKey,AttributeType=S \
      --key-schema \
        AttributeName=SkierID,KeyType=HASH \
        AttributeName=SeasonDayTime,KeyType=RANGE \
      --provisioned-throughput ReadCapacityUnits=100,WriteCapacityUnits=1000 \
      --global-secondary-indexes '[
        {
          "IndexName": "ResortSeasonDayIndex",
          "KeySchema": [
            {"AttributeName": "ResortSeasonDayKey", "KeyType": "HASH"},
            {"AttributeName": "SkierID", "KeyType": "RANGE"}
          ],
          "Projection": {
            "ProjectionType": "ALL"
          },
          "ProvisionedThroughput": {
            "ReadCapacityUnits": 100,
            "WriteCapacityUnits": 1000
          }
        }
      ]'
 ```

### 3. If need to delete table, run:
```angular2svg
    aws dynamodb delete-table --table-name lift_rides
```
    
    
### 4. If need to scan table, run:
```angular2svg
     aws dynamodb scan --table-name lift_rides --select "COUNT"
```
