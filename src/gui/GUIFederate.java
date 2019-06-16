package gui;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

public class GUIFederate extends Application {

    public static final String READY_TO_RUN = "ReadyToRun";
    public static final String time = "Średni czas oczekiwania: ";
    public static final String queueSize = "Długość kolejki: ";

    private int lastQueueSize = 0;
    private double lastWaitingTimeAvg = 0;


    Label avgTimeLabel = new Label(time);
    Label avgQueueSizeLabel = new Label(queueSize);

    NumberAxis xAxisTime = new NumberAxis();
    NumberAxis yAxisTime = new NumberAxis();
    NumberAxis xAxisLength = new NumberAxis();
    NumberAxis yAxisLength = new NumberAxis();

     LineChart<Number,Number> queueSizeChart =
            new LineChart<Number,Number>(xAxisLength,yAxisLength);

    LineChart<Number,Number> waitingTimeChart =
            new LineChart<Number,Number>(xAxisTime,yAxisTime);

    LineChart.Series<Number, Number> timeSeries = new LineChart.Series<>();
    LineChart.Series<Number, Number> queueSizeSeries = new LineChart.Series<>();

    private RTIambassador rtiAmbassador;
    private GUIAmbassador guiAmbassador;


    public static void main(String[] args) throws IOException {
        try {
            new GUIFederate().runFederate();
        } catch (RTIexception rtie) {
            rtie.printStackTrace();
        }
    }

    public void runFederate() throws RTIexception, IOException {
        rtiAmbassador = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();
        try {

            File fom = new File("restaurant.fed");
            rtiAmbassador.createFederationExecution("Federation - Restaurant",
                    fom.toURI().toURL());
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        guiAmbassador = new GUIAmbassador(this);
        rtiAmbassador.joinFederationExecution("GUIFederate", "Federation - Restaurant", guiAmbassador);
        log("Joined Federation as GuiFederate");

        rtiAmbassador.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (guiAmbassador.isAnnounced == false) {
            rtiAmbassador.tick();
        }

        waitForUser();

        rtiAmbassador.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");

        while (guiAmbassador.isReadyToRun == false) {
            rtiAmbassador.tick();
        }
        enableTimePolicy();

        publishAndSubscribe();

        this.createGui();







        while (guiAmbassador.running) {

            log("advanceTime!!!");
            advanceTime(1.0);
        }
    }

    private void createGui(){
        String avgTime = new String("Sredni czas oczekiwania");
        String queSize = new String("Dlugosc kolejki");

        queueSizeChart.setTitle(queSize);
        waitingTimeChart.setTitle(avgTime);

        timeSeries.setName("Sredni czas oczekiwania");
        queueSizeSeries.setName("Dlugosc kolejki");
        Platform.setImplicitExit(false);

        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setTitle("Restaurant");
            GridPane layout = new GridPane();


            layout.add(avgTimeLabel, 1, 1);
            layout.add(avgQueueSizeLabel, 1, 2);
            layout.add(waitingTimeChart, 1, 4);
            layout.add(queueSizeChart, 2, 4);

            Scene scene = new Scene(layout);
            stage.setScene(scene);
            stage.show();
        });

        queueSizeChart.getData().add(queueSizeSeries);
        waitingTimeChart.getData().add(timeSeries);

    }

    public void updateGUI(int queueSize, double avgWaitingTime, double time) {
        String avgTimeString = GUIFederate.time + String.valueOf(avgWaitingTime);
        String avgSizeString = GUIFederate.queueSize + String.valueOf(queueSize);
        log("aaa" + String.valueOf(queueSize)+" "+String.valueOf(avgWaitingTime));
        runThread(avgTimeString,avgSizeString,avgWaitingTime,queueSize);
    }

    private void runThread(String avgTimeString, String avgLengthString, double waitingTime, int queueSize) {
        Platform.runLater(() -> {
            avgTimeLabel.setText(avgTimeString);
            avgQueueSizeLabel.setText(avgLengthString);

            if(queueSize!=lastQueueSize){
                queueSizeSeries.getData().add(new LineChart.Data(guiAmbassador.federateTime, queueSize));
                lastQueueSize = queueSize;
            }

            if(waitingTime!=lastWaitingTimeAvg){
                timeSeries.getData().add(new LineChart.Data(guiAmbassador.federateTime, waitingTime));
                lastWaitingTimeAvg = waitingTime;
            }



        });
    }

    private void log(String message) {
        System.out.println("GuiFederate   : " + message);
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

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime(guiAmbassador.federateTime);
        LogicalTimeInterval lookahead = convertInterval(guiAmbassador.federateLookahead);

        this.rtiAmbassador.enableTimeRegulation(currentTime, lookahead);
        while (guiAmbassador.isRegulating == false) {
            rtiAmbassador.tick();
        }

        this.rtiAmbassador.enableTimeConstrained();
        while (guiAmbassador.isConstrained == false) {
            rtiAmbassador.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {
        int classHandle = rtiAmbassador.getObjectClassHandle("ObjectRoot.Statistics");
        int queueSizeHandle = rtiAmbassador.getAttributeHandle("queueSize", classHandle);
        int waitingTimeHandle = rtiAmbassador.getAttributeHandle("waitingTime", classHandle);

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(queueSizeHandle);
        attributes.add(waitingTimeHandle);

        rtiAmbassador.subscribeObjectClassAttributes(classHandle, attributes);
    }

    private void advanceTime(double timestep) throws RTIexception {
        guiAmbassador.isAdvancing = true;
        LogicalTime newTime = convertTime(guiAmbassador.federateTime + timestep);
        rtiAmbassador.timeAdvanceRequest(newTime);
        while (guiAmbassador.isAdvancing) {
            rtiAmbassador.tick();
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
