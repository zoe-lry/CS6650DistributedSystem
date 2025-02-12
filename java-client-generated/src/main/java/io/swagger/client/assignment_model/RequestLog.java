package io.swagger.client.assignment_model;

public class RequestLog {
  private Long startTime;
  private Long endTime;
  private Integer statusCode;
  private Integer attempt;
  private Integer consumerID;
  private String requestType;

  public RequestLog(Long startTime, Long endTime, Integer statusCode, Integer attempt,
      Integer consumerID, String requestType) {
    this.startTime = startTime;
    this.endTime = endTime;
    this.statusCode = statusCode;
    this.attempt = attempt;
    this.consumerID = consumerID;
    this.requestType = requestType;
  }

  public Integer getConsumerID() {
    return consumerID;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public int getAttempt() {
    return attempt;
  }

  public String getRequestType() {
    return requestType;
  }
}
