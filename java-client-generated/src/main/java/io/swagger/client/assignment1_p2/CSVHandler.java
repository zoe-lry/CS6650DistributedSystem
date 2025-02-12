package io.swagger.client.assignment1_p2;

import io.swagger.client.assignment_model.RequestLog;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CSVHandler {

   // Writes the request logs to a CSV file.
  public static void writeLogsToCSV(List<RequestLog> logs, String filePath) {
    try (FileWriter writer = new FileWriter(filePath)) {
      // Write CSV header
      writer.append("Start Time,End Time,Latency (ms),Status Code,Attempt,Consumer ID,Request Type\n");

      // Write each log entry
      for (RequestLog log : logs) {
        long latency = log.getEndTime() - log.getStartTime();  // Calculate request duration

        writer.append(log.getStartTime() + ",")
            .append(log.getEndTime() + ",")
            .append(latency + ",")
            .append(log.getStatusCode() + ",")
            .append(log.getAttempt() + ",")
            .append(log.getConsumerID() + ",")
            .append(log.getRequestType())
            .append("\n");
      }

      System.out.println("Logs successfully written to " + filePath);
    } catch (IOException e) {
      System.err.println("Error writing logs to CSV");
      e.printStackTrace();
    }
  }

  /**
   * mean response time (millisecs)
   * median response time (millisecs)
   * throughput = total number of requests/wall time (requests/second)
   * p99 (99th percentile) response time.
   * min and max response time (millisecs)
   */
  public static void printResponseTimeStats(List<RequestLog> logs) {
    if (logs.isEmpty()) {
      System.out.println("No logs available to calculate statistics.");
      return;
    }

    List<Long> responseTimes = logs.stream()
        .map(log -> log.getEndTime() - log.getStartTime())
        .sorted()
        .collect(Collectors.toList());

    long totalRequests = responseTimes.size();
    long totalResponseTime = responseTimes.stream().mapToLong(Long::longValue).sum();

    // Mean Response Time
    double meanResponseTime = (double) totalResponseTime / totalRequests;

    // Median Response Time
    double medianResponseTime = totalRequests % 2 == 0
        ? (responseTimes.get((int) totalRequests / 2 - 1) + responseTimes.get((int) totalRequests / 2)) / 2.0
        : responseTimes.get((int) totalRequests / 2);

    // Throughput: Total Requests / Wall Time (in seconds)
    long wallStartTime = logs.stream().min(Comparator.comparingLong(RequestLog::getStartTime)).get().getStartTime();
    long wallEndTime = logs.stream().max(Comparator.comparingLong(RequestLog::getEndTime)).get().getEndTime();
    double wallTimeInSeconds = (wallEndTime - wallStartTime) / 1000.0;
    double throughput = totalRequests / wallTimeInSeconds;

    // P99 Response Time (99th percentile)
    int p99Index = (int) Math.ceil(0.99 * totalRequests) - 1;
    long p99ResponseTime = responseTimes.get(p99Index);
//    int p999Index = (int) Math.ceil(0.999 * totalRequests) - 1;
//    long p999ResponseTime = responseTimes.get(p999Index);
    // Min and Max Response Time
    long minResponseTime = responseTimes.get(0);
    long maxResponseTime = responseTimes.get(responseTimes.size() - 1);

    // Print Statistics
    System.out.println("\n\nResponse Time Statistics:");
    System.out.printf("Mean Response Time      : %.2f ms%n", meanResponseTime);
    System.out.printf("Median Response Time    : %.2f ms%n", medianResponseTime);
    System.out.printf("Throughput              : %.2f requests/second%n", throughput);
    System.out.printf("P99 Response Time       : %d ms%n", p99ResponseTime);
//    System.out.printf("P999 Response Time      : %d ms%n", p999ResponseTime);
    System.out.printf("Min Response Time       : %d ms%n", minResponseTime);
    System.out.printf("Max Response Time       : %d ms%n", maxResponseTime);
  }

}
