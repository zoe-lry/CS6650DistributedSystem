package model;

import com.rabbitmq.client.Channel;

public class QueuedMessage {

  public final Channel channel;
  public final long deliveryTag;
  public final LiftRideEvent event;

  public QueuedMessage(Channel channel, long deliveryTag, LiftRideEvent event) {
    this.channel = channel;
    this.deliveryTag = deliveryTag;
    this.event = event;
  }

  public Channel getChannel() {
    return channel;
  }

  public long getDeliveryTag() {
    return deliveryTag;
  }

  public LiftRideEvent getEvent() {
    return event;
  }
}
