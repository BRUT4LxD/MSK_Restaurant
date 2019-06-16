package customer;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class CustomerFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private CustomerAmbassador fedamb;
    private int customerId;

    public void runFederate() throws RTIexception {
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
            File fom = new File( "restaurant.fed" );
            rtiamb.createFederationExecution( "Federation - Restaurant",
                    fom.toURI().toURL() );
            log( "Created Federation" );
        }
        catch( FederationExecutionAlreadyExists exists ) {
            log( "Didn't create federation, it already existed" );
        }
        catch( MalformedURLException urle ) {
            log( "Exception processing fom: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new CustomerAmbassador(this);
        rtiamb.joinFederationExecution( "CustomerFederate", "Federation - Restaurant", fedamb );
        log( "Joined Federation as CustomerFederate");

        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );

        while( fedamb.isAnnounced == false ) {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );

        while( fedamb.isReadyToRun == false ) {
            rtiamb.tick();
        }
        enableTimePolicy();

        publishAndSubscribe();

        while (fedamb.running) {
                createClient(fedamb.federateTime + fedamb.federateLookahead);
                advanceTime(randomTime());
                rtiamb.tick();
        }
    }

    private void waitForUser() {
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try {
            reader.readLine();
        }
        catch( Exception e ) {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        this.rtiamb.enableTimeRegulation( currentTime, lookahead );
        while( fedamb.isRegulating == false ) {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();
        while( fedamb.isConstrained == false ) {
            rtiamb.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {
        int addCustomerHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.AddCustomer" );
        rtiamb.publishInteractionClass(addCustomerHandle);
    }

    private void advanceTime( double timestep ) throws RTIexception
    {
   //     log("requesting time advance for: " + timestep);
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( newTime );
        while( fedamb.isAdvancing ) {
            rtiamb.tick();
        }
    }

    private double randomTime() {
        Random r = new Random();
        return 10 +(9 * r.nextDouble());

    }


    private void createClient(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        customerId++;
        byte[] id = EncodingHelpers.encodeInt(customerId);
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddCustomer");
        int customerHandle = rtiamb.getParameterHandle( "id", interactionHandle );

        parameters.add(customerHandle, id);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
        log("Dodano nowego kilenta, id: "+String.valueOf(customerId)+" aa: "+String.valueOf(fedamb.federateTime));
    }


    private LogicalTime convertTime(double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval( time );
    }

    private void log( String message )
    {
        System.out.println( "QueueFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new CustomerFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

}
