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
    private RTIambassador rtiAmbassador;
    private CustomerQueueAmbassador customerQueueAmbassador;

    public static void main(String[] args) {
        try {
            new CustomerQueueFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void runFederate() throws Exception {

        rtiAmbassador = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
//            File fom = new File("restaurant.fed");
//            rtiAmbassador.createFederationExecution("Federation - Restaurant",
//                    fom.toURI().toURL());
            rtiAmbassador.createFederationExecution( "Federation - Restaurant", (new File("restaurant.xml")).toURI().toURL() );
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        customerQueueAmbassador = new CustomerQueueAmbassador(this);
        rtiAmbassador.joinFederationExecution("CustomerQueueFederate", "Federation - Restaurant", customerQueueAmbassador);
        log("Joined Federation as CustomerQueueFederate");

        rtiAmbassador.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (customerQueueAmbassador.isAnnounced == false) {
            rtiAmbassador.tick();
        }

        waitForUser();

        rtiAmbassador.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (customerQueueAmbassador.isReadyToRun == false) {
            rtiAmbassador.tick();
        }

        enableTimePolicy();

        publishAndSubscribe();

        registerObject();

        while (customerQueueAmbassador.running) {
            double timeToAdvance = customerQueueAmbassador.federateTime + timeStep;
           advanceTime(timeToAdvance);


            if (customerQueueAmbassador.externalEvents.size() > 0) {
                customerQueueAmbassador.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : customerQueueAmbassador.externalEvents) {
                    customerQueueAmbassador.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case ADD:
                            this.addCustomerToQueue(externalEvent.getCustomerId());
                            break;

                        case GET:
                            this.getCustomerFromQueue(timeToAdvance + timeStep);
                            break;
                    }
                }
                customerQueueAmbassador.externalEvents.clear();
            }
            if (customerQueueAmbassador.grantedTime == timeToAdvance) {
                timeToAdvance += customerQueueAmbassador.federateLookahead;
                updateQueueSize(timeToAdvance);
                this.removeImpatientUser();
                customerQueueAmbassador.federateTime = timeToAdvance;
            }
            rtiAmbassador.tick();
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
            customer.setRemoveTime(customerQueueAmbassador.federateTime);
            customer.calculateWaitingTime();
            double waitingTime = customer.getWatingTime();
            this.updateWaitingTime(time, waitingTime);
            customerList.removeFirst();
            log("Remove customer from queue " + String.valueOf(customerList.size()));
        }
    }

    private void addCustomerToQueue(int id) {
        Customer customer = new Customer(id, customerQueueAmbassador.federateTime);
        customer.setImpatient(this.randomImpatient());
        customerList.add(customer);
        log("Add customer to queue " + String.valueOf(customerList.size()));
    }


    private void updateQueueSize(double time) throws RTIexception {
        log("Update queue size " + String.valueOf(customerList.size()));
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiAmbassador.getObjectClass(queueHlaHandle);
        int queueHandle = rtiAmbassador.getAttributeHandle("size", classHandle);
        byte[] queueSize = EncodingHelpers.encodeInt(customerList.size());

        attributes.add(queueHandle, queueSize);
        LogicalTime logicalTime = convertTime(time);
        rtiAmbassador.updateAttributeValues(queueHlaHandle, attributes, "actualize queue".getBytes(), logicalTime);
    }

    private void updateWaitingTime(double time, double waitingTime) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiAmbassador.getObjectClass(timeHlaHandle);
        int timeHandle = rtiAmbassador.getAttributeHandle("time", classHandle);
        byte[] waitingTimeByte = EncodingHelpers.encodeDouble(waitingTime);
        log("TEST22 " + String.valueOf(waitingTime));
        attributes.add(timeHandle, waitingTimeByte);
        LogicalTime logicalTime = convertTime(time);
        rtiAmbassador.updateAttributeValues(timeHlaHandle, attributes, "actualize queue".getBytes(), logicalTime);
    }


    private void registerObject() throws RTIexception {
        int classHandle = rtiAmbassador.getObjectClassHandle("ObjectRoot.Queue");
        this.queueHlaHandle = rtiAmbassador.registerObjectInstance(classHandle);

        classHandle = rtiAmbassador.getObjectClassHandle("ObjectRoot.WaitingTime");
        this.timeHlaHandle = rtiAmbassador.registerObjectInstance(classHandle);
    }

    private void publishAndSubscribe() throws RTIexception {
        int classHandle = rtiAmbassador.getObjectClassHandle("ObjectRoot.Queue");
        int sizeHandle = rtiAmbassador.getAttributeHandle("size", classHandle);

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(sizeHandle);

        rtiAmbassador.publishObjectClass(classHandle, attributes);

        classHandle = rtiAmbassador.getObjectClassHandle("ObjectRoot.WaitingTime");
        sizeHandle = rtiAmbassador.getAttributeHandle("time", classHandle);

        attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(sizeHandle);

        rtiAmbassador.publishObjectClass(classHandle, attributes);

        int addCustomerHandle = rtiAmbassador.getInteractionClassHandle("InteractionRoot.AddCustomer");
        customerQueueAmbassador.addCustomerHandle = addCustomerHandle;
        rtiAmbassador.subscribeInteractionClass(addCustomerHandle);

        int getCustomerHandle = rtiAmbassador.getInteractionClassHandle("InteractionRoot.GetCustomer");
        customerQueueAmbassador.getCustomerHandle = getCustomerHandle;
        rtiAmbassador.subscribeInteractionClass(getCustomerHandle);
    }

    private void advanceTime(double timeToAdvance) throws RTIexception {
        customerQueueAmbassador.isAdvancing = true;
        LogicalTime newTime = convertTime(timeToAdvance);
        rtiAmbassador.timeAdvanceRequest(newTime);

        while (customerQueueAmbassador.isAdvancing) {
            rtiAmbassador.tick();
        }
    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime(customerQueueAmbassador.federateTime);
        LogicalTimeInterval lookahead = convertInterval(customerQueueAmbassador.federateLookahead);

        this.rtiAmbassador.enableTimeRegulation(currentTime, lookahead);
        while (customerQueueAmbassador.isRegulating == false) {
            rtiAmbassador.tick();
        }

        this.rtiAmbassador.enableTimeConstrained();
        while (customerQueueAmbassador.isConstrained == false) {
            rtiAmbassador.tick();
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
        int randomInt = random.nextInt(4);
        if (randomInt == 3) return true;
        else return false;
    }
}
