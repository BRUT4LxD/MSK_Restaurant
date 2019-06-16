package customerQueue;

import dao.Customer;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class CustomerQueueFederate {
    public static final String READY_TO_RUN = "ReadyToRun";
    private final double timeStep = 1.0;
    protected LinkedList<Customer> customerList = new LinkedList<>();
    protected int timeHlaHandle = 0;
    protected int queueHlaHandle = 0;
    private RTIambassador rtiamb;
    private CustomerQueueAmbassador fedamb;

    public static void main(String[] args) {
        try {
            new CustomerQueueFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void runFederate() throws Exception {

        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
            File fom = new File("restaurant.fed");
            rtiamb.createFederationExecution("Federation - Restaurant",
                    fom.toURI().toURL());
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        fedamb = new CustomerQueueAmbassador(this);
        rtiamb.joinFederationExecution("CustomerQueueFederate", "Federation - Restaurant", fedamb);
        log("Joined Federation as CustomerQueueFederate");

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (fedamb.isAnnounced == false) {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.tick();
        }

        enableTimePolicy();

        publishAndSubscribe();

        registerObject();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
           advanceTime(timeToAdvance);


            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case ADD:
                            this.addCustomerToQueue(externalEvent.getCustomerId());
                            break;

                        case GET:
                            this.getCustomerFromQueue(timeToAdvance + timeStep);
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }
            if (fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                updateQueueSize(timeToAdvance);
                this.removeImpatientUser();
                fedamb.federateTime = timeToAdvance;
            }
            rtiamb.tick();
        }
    }

    private void removeImpatientUser() {
        List<Customer> customerToRemove = new ArrayList<>();
        for (Customer customer : customerList) {
            if (customer.isImpatient()) customerToRemove.add(customer);
        }
        for (Customer customer : customerToRemove) {
            customerList.remove(customer);
            log("Usunieto niecierpliwych klientow");
        }
    }

    private void getCustomerFromQueue(double time) throws RTIexception {
        if (customerList.size() > 0) {
            Customer customer = customerList.getFirst();
            customer.setRemoveTime(fedamb.federateTime);
            customer.calculateWaitingTime();
            double waitingTime = customer.getWatingTime();
            this.updateWaitingTime(time, waitingTime);
            customerList.removeFirst();
            log("Remove customer from queue " + String.valueOf(customerList.size()));
        }
    }

    private void addCustomerToQueue(int id) {
        Customer customer = new Customer(id, fedamb.federateTime);
        customer.setImpatient(this.randomImpatient());
        customerList.add(customer);
        log("Add customer to queue " + String.valueOf(customerList.size()));
    }


    private void updateQueueSize(double time) throws RTIexception {
        log("Update queue size " + String.valueOf(customerList.size()));
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(queueHlaHandle);
        int queueHandle = rtiamb.getAttributeHandle("size", classHandle);
        byte[] queueSize = EncodingHelpers.encodeInt(customerList.size());

        attributes.add(queueHandle, queueSize);
        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(queueHlaHandle, attributes, "actualize queue".getBytes(), logicalTime);
    }

    private void updateWaitingTime(double time, double waitingTime) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(timeHlaHandle);
        int timeHandle = rtiamb.getAttributeHandle("time", classHandle);
        byte[] waitingTimeByte = EncodingHelpers.encodeDouble(waitingTime);
        log("TEST22 " + String.valueOf(waitingTime));
        attributes.add(timeHandle, waitingTimeByte);
        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(timeHlaHandle, attributes, "actualize queue".getBytes(), logicalTime);
    }


    private void registerObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        this.queueHlaHandle = rtiamb.registerObjectInstance(classHandle);

        classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingTime");
        this.timeHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void publishAndSubscribe() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int sizeHandle = rtiamb.getAttributeHandle("size", classHandle);

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(sizeHandle);

        rtiamb.publishObjectClass(classHandle, attributes);

        classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingTime");
        sizeHandle = rtiamb.getAttributeHandle("time", classHandle);

        attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(sizeHandle);

        rtiamb.publishObjectClass(classHandle, attributes);

        int addCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddCustomer");
        fedamb.addCustomerHandle = addCustomerHandle;
        rtiamb.subscribeInteractionClass(addCustomerHandle);

        int getCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.GetCustomer");
        fedamb.getCustomerHandle = getCustomerHandle;
        rtiamb.subscribeInteractionClass(getCustomerHandle);
    }

    private void advanceTime(double timeToAdvance) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(timeToAdvance);
        rtiamb.timeAdvanceRequest(newTime);

        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        this.rtiamb.enableTimeRegulation(currentTime, lookahead);
        while (fedamb.isRegulating == false) {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();
        while (fedamb.isConstrained == false) {
            rtiamb.tick();
        }
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private LogicalTime convertTime(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTime(time);
    }

    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }

    private void log(String message) {
        System.out.println("QueueFederate   : " + message);
    }

    public boolean randomImpatient() {
        Random random = new Random();
        int randomInt = random.nextInt(10);
        if (randomInt == 3) return true;
        else return false;
    }
}
