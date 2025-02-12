package io.swagger.client.assignment_test;

import io.swagger.client.assignment_model.LiftRideEvent;
import java.util.ArrayDeque;
import java.util.Deque;

public class Buffer {
  private Deque<LiftRideEvent> buffer;
  private Boolean isDone;

  public Buffer() {
    this.buffer = new ArrayDeque<>();
    this.isDone = false;
  }

  public Deque<LiftRideEvent> getBuffer() {
    return buffer;
  }

  public void setBuffer(Deque<LiftRideEvent> buffer) {
    this.buffer = buffer;
  }

  public Boolean getDone() {
    return isDone;
  }

  public synchronized void setDone(Boolean done) {
    isDone = done;
    notifyAll();
  }
// True if consumer must wait for producer to send message,
  // false if producer must wait for consumer to retrieve message.
//  private boolean empty = true;

  public synchronized LiftRideEvent retrieve() {


    // Wait until message is available.
    while (this.buffer.isEmpty()) {
      if (this.isDone) {
        return null;
      }
      try {
        System.out.println("Waiting for a message");
        wait();
      } catch (InterruptedException e) {}
    }
    // Toggle status.
    notifyAll();
    return buffer.removeFirst();
  // Notify producer that buffer is empty

  }

  public synchronized void put(LiftRideEvent liftRideEvent) {
    // Wait if there are more than 500 events
    while (this.buffer.size() >= 10) {
      try {
        wait();
      } catch (InterruptedException e) {}
    }
    // Store message.
    this.buffer.addLast(liftRideEvent);
    // Notify consumer that message is available
    notifyAll();
  }

}
