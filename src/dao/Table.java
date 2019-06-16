package dao;

public class Table {

    private int id;
    private boolean isAvailable;
    private double serviceTime;

    public Table(int id, boolean isAvailable) {
        this.id = id;
        this.isAvailable = isAvailable;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public double getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(double serviceTime) {
        this.serviceTime = serviceTime;
    }
}
