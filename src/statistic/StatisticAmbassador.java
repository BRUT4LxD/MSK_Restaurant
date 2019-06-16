package statistic;

import customerQueue.ExternalEvent;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;

import java.util.ArrayList;

public class StatisticAmbassador extends NullFederateAmbassador {

    protected boolean running = true;

    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;
    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    protected int timeHandle = 0;
    protected int queueHandle = 0;
    protected int statisticHandle = 0;


    protected StatisticFederate fed;

    public StatisticAmbassador(StatisticFederate fed) {
        this.fed = fed;
    }

    private double convertTime(LogicalTime logicalTime) {
        // PORTICO SPECIFIC!!
        return ((DoubleTime) logicalTime).getTime();
    }

    private void log(String message) {
        System.out.println("StatisticsAmbassador: " + message);
    }

    public void synchronizationPointRegistrationFailed(String label) {
        log("Failed to register sync point: " + label);
    }

    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(StatisticFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    public void federationSynchronized(String label) {
        log("Federation Synchronized: " + label);
        if (label.equals(StatisticFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    public void timeRegulationEnabled(LogicalTime theFederateTime) {
        this.federateTime = convertTime(theFederateTime);
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled(LogicalTime theFederateTime) {
        this.federateTime = convertTime(theFederateTime);
        this.isConstrained = true;
    }

    public void timeAdvanceGrant(LogicalTime theTime) {
        this.federateTime = convertTime(theTime);
        this.isAdvancing = false;
    }

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag) {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        receiveInteraction(interactionClass, theInteraction, tag, null, null);
    }

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag,
                                   LogicalTime theTime,
                                   EventRetractionHandle eventRetractionHandle) {
//        StringBuilder builder = new StringBuilder("Interaction Received:");
//        if (interactionClass == koniecObslugiHandle) {
//            try {
//                int numberQueue = EncodingHelpers.decodeInt(theInteraction.getValue(0));
//                fed.summaryList.set(numberQueue - 1, fed.summaryList.get(numberQueue - 1) + 1);
//                builder.append(" KoniecObslugi , queue=" + numberQueue);
//            } catch (ArrayIndexOutOfBounds ignored) {
//            }
//        } else {
//            if (interactionClass == dlugoscKolejkiHandle) {
//                try {
//                    builder.append(" dlugosckolejki , queue=");
//                    int numberQueue = EncodingHelpers.decodeInt(theInteraction.getValue(0));
//                    int clientAmount = EncodingHelpers.decodeInt(theInteraction.getValue(1));
//                    fed.lengthUpdate(numberQueue, clientAmount);
//                } catch (ArrayIndexOutOfBounds ignored) {
//                }
//            } else {
//                if (interactionClass == czasOczekiwaniaHandle) {
//                    try {
//
//                        int numberQueue = EncodingHelpers.decodeInt(theInteraction.getValue(1));
//                        float time = EncodingHelpers.decodeFloat(theInteraction.getValue(0));
//                        builder.append(" czasoczewkiania , queue=" + numberQueue);
//                        log(builder.toString());
//                        fed.timeUpdate(numberQueue, time);
//                    } catch (ArrayIndexOutOfBounds ignored) {
//                    }
//                }
//            }
//        }

    }

    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag) {
        reflectAttributeValues(theObject, theAttributes, tag, null, null);
    }


    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
                                       EventRetractionHandle retractionHandle) {

        for (int i = 0; i < theAttributes.size(); i++) {
            try {
            if (theObject == timeHandle) {
                double time = convertTime(theTime);
                double waitingTime = EncodingHelpers.decodeDouble(theAttributes.getValue(i));
                System.out.println(theAttributes);
                externalEvents.add(new ExternalEvent(waitingTime, ExternalEvent.EventType.TIME, time));
                log("Czas oczekiwania " + String.valueOf(waitingTime));

            } else {
                    int queueSize = EncodingHelpers.decodeInt(theAttributes.getValue(i));
                    double time = convertTime(theTime);
                    externalEvents.add(new ExternalEvent(queueSize, ExternalEvent.EventType.ADD, time));
                    log("Zmieniono wartosc kolejki, rozmiar: " + String.valueOf(queueSize));
                }
            }catch (ArrayIndexOutOfBounds aioob) {
            }
        }


    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        if(theObjectClass==fed.queueHlaHandle) this.queueHandle = theObject;
        else if(theObjectClass==fed.timeHlaHandle) this.timeHandle = theObject;
    }

}
