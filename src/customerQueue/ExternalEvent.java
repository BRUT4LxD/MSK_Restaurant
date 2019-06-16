package customerQueue;


import java.util.Comparator;

public class ExternalEvent {

    public enum EventType {ADD, GET,TIME}

    private  int customerId;
    private EventType eventType;
    private Double time;
    private double waitingTime;

    public ExternalEvent(int customerId, EventType eventType, Double time) {
        this.customerId = customerId;
        this.eventType = eventType;
        this.time = time;
    }

    public ExternalEvent(double waitingTime, EventType eventType, Double time) {
        this.waitingTime = waitingTime;
        this.eventType = eventType;
        this.time = time;
    }

    public ExternalEvent(EventType eventType, Double time) {
        this.eventType = eventType;
        this.time = time;
    }

    public EventType getEventType() {
        return eventType;
    }

    public int getCustomerId() {
        return customerId;
    }

    public double getTime() {
        return time;
    }

    public double getWaitingTime() {
        return waitingTime;
    }

    public static class ExternalEventComparator implements Comparator<ExternalEvent> {

        @Override
        public int compare(ExternalEvent o1, ExternalEvent o2) {
            return o1.time.compareTo(o2.time);
        }
    }

}
