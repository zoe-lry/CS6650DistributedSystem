package io.swagger.client.assignment_test;


import io.swagger.client.model.LiftRide;
import io.swagger.client.assignment_model.LiftRideEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class Producer implements Runnable {
  private final BlockingQueue<LiftRideEvent> queue;
  private final Integer totalNumOfEvents;

  public Producer(BlockingQueue<LiftRideEvent> queue, Integer totalNumOfEvents) {
    this.queue = queue;
    this.totalNumOfEvents = totalNumOfEvents;
  }

  @Override
  public void run() {
    // Produce events
    IntStream.range(0, totalNumOfEvents).forEach(i ->{
      try {
        addNewEvent();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });
    //    System.out.println("[Producer] Producer done.");
  }

  private void addNewEvent() throws InterruptedException {
    // Create a new LiftRide (the body/payload)
    LiftRide body = new LiftRide();
    int randomLiftID = ThreadLocalRandom.current().nextInt(1, 41);
    int randomTime = ThreadLocalRandom.current().nextInt(1, 361);
    body.setLiftID(randomLiftID);
    body.setTime(randomTime);

    int randomSkierID = ThreadLocalRandom.current().nextInt(1, 100001);
    int randomResortID = ThreadLocalRandom.current().nextInt(1, 11);
    String seasonID = "2025";
    String dayID = "1";
    queue.put(new LiftRideEvent(body, randomResortID, seasonID, dayID, randomSkierID));
  }

}
