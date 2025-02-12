package io.swagger.client.assignment_model;

import io.swagger.client.model.LiftRide;

public class LiftRideEvent {
  private static final int POISON_ID = -1;
  private LiftRide body;
  private Integer resortID ; // Integer | ID of the resort of interest
  private String seasonID ;
  private String dayID ;
  private Integer skierID ;

  public LiftRideEvent(LiftRide body, Integer resortID, String seasonID, String dayID,
      Integer skierID) {
    this.body = body;
    this.resortID = resortID;
    this.seasonID = seasonID;
    this.dayID = dayID;
    this.skierID = skierID;
  }

  public static LiftRideEvent poisonPill() {
    return new LiftRideEvent(null, -1, "", "", POISON_ID);
  }

  public LiftRide getBody() {
    return body;
  }

  public void setBody(LiftRide body) {
    this.body = body;
  }

  public Integer getResortID() {
    return resortID;
  }

  public void setResortID(Integer resortID) {
    this.resortID = resortID;
  }

  public String getSeasonID() {
    return seasonID;
  }

  public void setSeasonID(String seasonID) {
    this.seasonID = seasonID;
  }

  public String getDayID() {
    return dayID;
  }

  public void setDayID(String dayID) {
    this.dayID = dayID;
  }

  public Integer getSkierID() {
    return skierID;
  }

  public void setSkierID(Integer skierID) {
    this.skierID = skierID;
  }

  @Override
  public String toString() {
    return "LiftRideEvent{" +
        "body=" + body +
        ", resortID=" + resortID +
        ", seasonID='" + seasonID + '\'' +
        ", dayID='" + dayID + '\'' +
        ", skierID=" + skierID +
        '}';
  }

  public boolean isPoisonPill() {
    return this.resortID == POISON_ID;

  }
}
