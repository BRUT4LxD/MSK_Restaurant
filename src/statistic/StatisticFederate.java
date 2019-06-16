package statistic;

import customerQueue.ExternalEvent;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.LinkedList;

public class StatisticFederate {


    public static final String READY_TO_RUN = "ReadyToRun";
    private RTIambassador rtiamb;
    private StatisticAmbassador fedamb;

    private double waitingTime = 0.0;
    private double avgWaitingTime = 0.0;
    private int count = 1;
    private int queueSize = 0;

    protected int timeHlaHandle = 0;
    protected int queueHlaHandle = 0;
    protected int statisticHandle= 0;

    public static void main(String[] args) {
        StatisticFederate sf = new StatisticFederate();

        try {
            sf.runFederate();
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

        fedamb = new StatisticAmbassador(this);
        rtiamb.joinFederationExecution("StatisticFederate", "Federation - Restaurant", fedamb);
        log("Joined Federation as " + "StatisticFederate");

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
        this.registerStorageObject();
        log(fedamb.federateTime + " Published and Subscribed");


        while (fedamb.running) {
            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case ADD:
                            this.updateQueueSize(externalEvent.getCustomerId(), fedamb.federateTime + fedamb.federateLookahead);
                            break;
                        case TIME:
                            System.out.println("CustomerId - " + externalEvent.getCustomerId());
                            System.out.println("waiting Time - " + externalEvent.getWaitingTime());
                            System.out.println("Current avg time - " + this.avgWaitingTime);
                            System.out.println("federateLookAhead - " + fedamb.federateLookahead);
                            System.out.println("federateTime- " + fedamb.federateTime);
                            this.updateWaitingTime(externalEvent.getWaitingTime(), fedamb.federateTime + fedamb.federateLookahead);
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }
            rtiamb.tick();
            advanceTime(1.0);
        }
    }

    private void updateWaitingTime(double waitingTime, double time) throws RTIexception {
        log("Statistics waitingTime: "+String.valueOf(waitingTime));
        this.waitingTime =this.waitingTime+ waitingTime;
        this.avgWaitingTime = this.waitingTime/count;
        count++;
        this.updateStatistics(time);
    }

    private void updateQueueSize(int queueSize, double time) throws RTIexception {
        log("Statistics queueSize: "+String.valueOf(queueSize));
        this.queueSize = queueSize;
        this.updateStatistics(time);
    }

    private void updateStatistics(double time) throws RTIexception{
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(statisticHandle);
        int sizeHandle = rtiamb.getAttributeHandle("queueSize", classHandle);
        int timeHandle = rtiamb.getAttributeHandle("waitingTime", classHandle);
        byte[] queueSizeBytes = EncodingHelpers.encodeInt(this.queueSize);
        byte[] waitingTimeBytes = EncodingHelpers.encodeDouble(this.avgWaitingTime);

        attributes.add(sizeHandle, queueSizeBytes);
        attributes.add(timeHandle, waitingTimeBytes);
        LogicalTime logicalTime = convertTime(time);
        log("TEST "+String.valueOf(this.avgWaitingTime)+"  aa: "+String.valueOf(fedamb.federateTime));
        rtiamb.updateAttributeValues(statisticHandle, attributes, "actualize statistics".getBytes(), logicalTime);
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

    private void advanceTime(double timestep) throws RTIexception {
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(newTime);
        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    private void registerStorageObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Statistics");
        statisticHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void publishAndSubscribe() throws RTIexception {

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Statistics");
        int queueSizeHandle = rtiamb.getAttributeHandle("queueSize", classHandle);
        int waitingTimeHandle = rtiamb.getAttributeHandle("waitingTime", classHandle);

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(queueSizeHandle);
        attributes.add(waitingTimeHandle);

        rtiamb.publishObjectClass(classHandle, attributes);

        classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int queueHandle = rtiamb.getAttributeHandle("size", classHandle);
        this.queueHlaHandle= classHandle;
        attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(queueHandle);

        rtiamb.subscribeObjectClassAttributes(classHandle, attributes);


        classHandle = rtiamb.getObjectClassHandle("ObjectRoot.WaitingTime");
        int timeHandle = rtiamb.getAttributeHandle("time", classHandle);
        this.timeHlaHandle= classHandle;
        attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(timeHandle);

        rtiamb.subscribeObjectClassAttributes(classHandle, attributes);


//        int getCustomerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.GetCustomer");
//        rtiamb.publishInteractionClass(getCustomerHandle);
         int a;

        //publish///////////////////////////
//        int statystykiHandle = rtiamb.getObjectClassHandle("HLAobjectRoot.Statystyki");
//        int srCzasOczekiwaniaWKolejceHandle = rtiamb.getAttributeHandle("srCzasOczekiwaniaWKolejce", statystykiHandle);
//        int srDlugoscKolejkiHandle = rtiamb.getAttributeHandle("srDlugoscKolejki", statystykiHandle);
//        int nrKasyHandle = rtiamb.getAttributeHandle("nrKasy", statystykiHandle);
//        int dlugoscKolejkiHandle = rtiamb.getAttributeHandle("dlugoscKolejki", statystykiHandle);
//        int czasOczekiwaniaHandle = rtiamb.getAttributeHandle("czasOczekiwania", statystykiHandle);
//        int liczbaKlientowHandle = rtiamb.getAttributeHandle("liczbaKlientow", statystykiHandle);
//
//        AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
//        attributes.add(srCzasOczekiwaniaWKolejceHandle);
//        attributes.add(srDlugoscKolejkiHandle);
//        attributes.add(nrKasyHandle);
//        attributes.add(dlugoscKolejkiHandle);
//        attributes.add(czasOczekiwaniaHandle);
//        attributes.add(liczbaKlientowHandle);
//
//        rtiamb.publishObjectClass(statystykiHandle, attributes);
//
//        //subscribe///////////////////////////////////
//        int liczbaKasHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.liczbaKas");
//        fedamb.liczbaKasHandle = liczbaKasHandle;
//        rtiamb.subscribeInteractionClass(liczbaKasHandle);
//
//        int koniecObslugiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.koniecObslugi");
//        fedamb.koniecObslugiHandle = koniecObslugiHandle;
//        rtiamb.subscribeInteractionClass(koniecObslugiHandle);
//
//        dlugoscKolejkiHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.dlugoscKolejki");
//        fedamb.dlugoscKolejkiHandle = dlugoscKolejkiHandle;
//        rtiamb.subscribeInteractionClass(dlugoscKolejkiHandle);
//
//        czasOczekiwaniaHandle = rtiamb.getInteractionClassHandle("HLAinteractionRoot.czasOczekiwania");
//        fedamb.czasOczekiwaniaHandle = czasOczekiwaniaHandle;
//        rtiamb.subscribeInteractionClass(czasOczekiwaniaHandle);


    }

    private void enableTimePolicy() throws RTIexception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(currentTime, lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.tick();
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.tick();
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
        System.out.println("StatisticsFederate  : " + message);
    }

}
