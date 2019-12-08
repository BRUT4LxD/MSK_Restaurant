package table;

import customerQueue.ExternalEvent;
import dao.Table;
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
import java.util.List;
import java.util.Random;

public class TableFederate {

    public static final String READY_TO_RUN = "ReadyToRun";
    private final double timeStep = 1.0;
    private final int numberOfTable = 15;
    private RTIambassador rtiamb;
    private TableAmbassador fedamb;
    private List<Table> tableList;

    public static void main(String[] args) {
        try {
            new TableFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

    public void runFederate() throws RTIexception {
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
//            File fom = new File("restaurant.fed");
//            rtiamb.createFederationExecution("Federation - Restaurant",
//                    fom.toURI().toURL());
            rtiamb.createFederationExecution( "Federation - Restaurant", (new File("restaurant.xml")).toURI().toURL() );
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        fedamb = new TableAmbassador(this);
        rtiamb.joinFederationExecution("TableFederation", "Federation - Restaurant", fedamb);
        log("Joined Federation as TableFederation");

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
        createTable(numberOfTable);
        publishAndSubscribe();

        while (fedamb.running) {

            advanceTime(1.0);
            this.waitOnCustomer(fedamb.federateTime );
            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case GET:
                            this.getCustomerFromQueue(externalEvent.getCustomerId(), fedamb.federateTime + fedamb.federateLookahead);
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }
            rtiamb.tick();
        }
    }


    private void waitOnCustomer(double theTime) throws RTIexception {

        for (Table table : tableList) {
            if (!table.isAvailable()) {
                if (table.getServiceTime() <= fedamb.federateTime) {
                    if(repeatOrder()){
                        table.setAvailable(false);
                        table.setServiceTime(fedamb.federateTime + this.randomServiceTime());
                        log("Ponowne zamowienie");
                    }else {
                        table.setAvailable(true);
                        table.setServiceTime(-1.0);
                        fedamb.externalEvents.add(new ExternalEvent(fedamb.queueSize, ExternalEvent.EventType.GET, theTime));
                        log("Koniec obslugi klienta: " + theTime);
                    }
                }
            }

        }
    }


    private void createTable(int numberOfTable) {
        tableList = new ArrayList<>(numberOfTable);
        for (int i = 0; i < numberOfTable; i++) {
            Table table = new Table(i, true);
            tableList.add(table);
        }
    }

    private void getCustomerFromQueue(int queueSize, double timeStep) throws RTIexception {
        for (int i = 0; i < queueSize && i < numberOfTable; i++) {
            for (Table table : tableList) {
                if (table.isAvailable()) {
                    table.setAvailable(false);
                    table.setServiceTime(fedamb.federateTime + this.randomServiceTime());
                    this.sendGetUserInterAction(timeStep);
                    log("Rozpoeczeto obsluge");
                    return;
                }
            }
        }
    }

    public int randomServiceTime(){
        Random random = new Random();
        return random.nextInt(500);
    }

    public boolean repeatOrder(){
        Random random = new Random();
        int randomInt = random.nextInt(10);
        if(randomInt==5)return true;
        else return false;
    }

    private void sendGetUserInterAction(double timeStep) throws RTIexception {
        SuppliedParameters parameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.GetCustomer");
        LogicalTime time = convertTime(timeStep);
        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
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

    private void publishAndSubscribe() throws RTIexception {

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int queueHandle = rtiamb.getAttributeHandle("size", classHandle);

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(queueHandle);

        rtiamb.subscribeObjectClassAttributes(classHandle, attributes);


        int getCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.GetCustomer");
        rtiamb.publishInteractionClass(getCustomerHandle);

    }

    private void advanceTime(double timestep) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(newTime);
        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTime(time);
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }

    private void log(String message) {
        System.out.println("QueueFederate   : " + message);
    }

    public void WaitOnCustomer(int queueSize) {

    }
}
