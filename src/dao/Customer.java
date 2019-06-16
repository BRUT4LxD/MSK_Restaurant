package dao;

public class Customer {
    private int ID;
    private double addTime;
    private double removeTime;
    private double waitingTime;
    private boolean isImpatient;

    public Customer(int ID, double addTime) {
        this.ID = ID;
        this.addTime = addTime;
        this.waitingTime=0.0;
    }

    public void setImpatient(boolean isImpatient){
        this.isImpatient=isImpatient;
    }

    public int getID() {
        return ID;
    }

    public double getWatingTime() {
        return waitingTime;
    }

    public void setWatingTime(double waitingTime) {
        this.waitingTime = waitingTime;
    }

    public boolean isImpatient() {
        return isImpatient;
    }

    public double getAddTime() {
        return addTime;
    }

    public void setAddTime(double addTime) {
        this.addTime = addTime;
    }

    public double getRemoveTime() {
        return removeTime;
    }

    public void setRemoveTime(double removeTime) {
        this.removeTime = removeTime;
    }

    public void calculateWaitingTime() {
        waitingTime = removeTime - addTime;
    }
}
