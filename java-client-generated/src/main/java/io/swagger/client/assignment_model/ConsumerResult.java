package io.swagger.client.assignment_model;

import io.swagger.client.assignment_model.RequestLog;
import java.util.List;

public class ConsumerResult {
  private final Integer numOfSuccess;
  private final Integer numOfFailures;
  private final List<RequestLog> logs;

  public ConsumerResult(Integer numOfSuccess, Integer numOfFailures, List<RequestLog> logs) {
    this.numOfSuccess = numOfSuccess;
    this.numOfFailures = numOfFailures;
    this.logs = logs;
  }

  public ConsumerResult(Integer numOfSuccess, Integer numOfFailures) {
    this.numOfSuccess = numOfSuccess;
    this.numOfFailures = numOfFailures;
    this.logs = null;
  }

  public Integer getNumOfSuccess() { return numOfSuccess; }
  public Integer getNumOfFailures() { return numOfFailures; }
  public List<RequestLog> getLogs() { return logs; }
}
