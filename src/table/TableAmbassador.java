package table;

import customer.CustomerFederate;
import customerQueue.ExternalEvent;
import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;

import java.util.ArrayList;

public class TableAmbassador extends NullFederateAmbassador {

    protected double federateTime = 0.0;

    protected double federateLookahead = 1.0;
    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;
    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;
    protected boolean running = true;
    protected int queueSize;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();
    private TableFederate fed;

    public TableAmbassador(TableFederate fed) {
        this.fed = fed;
    }

    private double convertTime(LogicalTime logicalTime) {
        // PORTICO SPECIFIC!!
        return ((DoubleTime) logicalTime).getTime();
    }

    private void log(String message) {
        System.out.println("FederateAmbassador: " + message);
    }

    public void synchronizationPointRegistrationFailed(String label) {
        log("Failed to register sync point: " + label);
    }

    public void synchronizationPointRegistrationSucceeded(String label) {
        log("Successfully registered sync point: " + label);
    }

    public void announceSynchronizationPoint(String label, byte[] tag) {
        log("Synchronization point announced: " + label);
        if (label.equals(CustomerFederate.READY_TO_RUN))
            this.isAnnounced = true;
    }

    public void federationSynchronized(String label) {
        log("Federation Synchronized: " + label);
        if (label.equals(CustomerFederate.READY_TO_RUN))
            this.isReadyToRun = true;
    }

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

    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag) {
        reflectAttributeValues(theObject, theAttributes, tag, null, null);
    }


    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
                                       EventRetractionHandle retractionHandle) {
        StringBuilder builder = new StringBuilder("Reflection for object:");

        builder.append(" handle=" + theObject);
        builder.append(", attributeCount=" + theAttributes.size());
        builder.append("\n");
        for (int i = 0; i < theAttributes.size(); i++) {
            try {
                // print the attibute handle
                builder.append("\tattributeHandle=");
                builder.append(theAttributes.getAttributeHandle(i));
                // print the attribute value
                builder.append(", attributeValue=");
                queueSize = EncodingHelpers.decodeInt(theAttributes.getValue(i));
                builder.append(queueSize);
                builder.append(", time=");
                builder.append(theTime);
                double time = convertTime(theTime);
                builder.append("\n");
                externalEvents.add(new ExternalEvent(queueSize, ExternalEvent.EventType.GET, time));
//                log("Zmieniono wartosc kolejki, rozmiar: "+String.valueOf(queueSize));
            } catch (ArrayIndexOutOfBounds aioob) {
                // won't happen
            }
        }


    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        System.out.println("Pojawil sie nowy obiekt typu SimObject");
    }
}
