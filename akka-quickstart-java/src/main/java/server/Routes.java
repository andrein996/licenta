package server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.model.headers.RawHeader;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import group.DeviceGroup;
import group.enums.HeatingSettings;
import manager.DeviceManager;
import model.BlockHeating;
import model.HeatingSetting;
import model.Home;
import model.Temperatures;
import scala.concurrent.duration.Duration;
import validator.HomeValidator;
import validator.ValidatorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.http.javadsl.server.PathMatchers.segment;

class Routes extends AllDirectives {

    //Status code constants
    private static final Iterable<HttpHeader> HTTP_HEADERS = new ArrayList<HttpHeader>() {
        {
            add(new RawHeader("Access-Control-Allow-Origin", "*"));
            add(new RawHeader("Access-Control-Allow-Credentials", "true"));
            add(new RawHeader("Access-Control-Allow-Headers", "Content-Type"));
            add(new RawHeader("Access-Control-Allow-Methods", "OPTIONS, POST, PUT, GET, DELETE"));
        }
    };

    private final ActorRef deviceManager;
    private final LoggingAdapter log;

    Routes(ActorSystem system, ActorRef deviceManager) {
        this.deviceManager = deviceManager;
        log = Logging.getLogger(system, this);
    }

    // Required by the `ask` (?) method below
    private Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS)); // usually we'd obtain the timeout from the system's configuration

    /**
     * This method creates one central route
     */
    //#all-routes
    //#users-get-delete
    Route routes() {
        return route(pathPrefix("api", () ->
                route(
                        getNumberOfHomesRoute(),
                        homesRoute()
                )
        ), pathPrefix("iot", () ->
                route(
                    getAllDevicesAndHomesRoute(),
                    postTemperatureToDevicesRoute(),
                    putAlterHomesHeatingSystem()
                )
        ));
    }

    private Route putAlterHomesHeatingSystem() {
        return path(segment("heating"), () -> route(
                put(this::alterHomesHeatingSystem),
                options(() -> complete(StatusCodes.OK, HTTP_HEADERS, "OK", Jackson.marshaller()))
        ));
    }

    private Route alterHomesHeatingSystem() {
        PatternsCS.ask(deviceManager, new DeviceManager.AlterHeatingSettings() , timeout);

        return complete(StatusCodes.OK, HTTP_HEADERS, "Heating system altered", Jackson.marshaller());
    }

    private Route getAllDevicesAndHomesRoute() {
        return pathEnd(() -> get(this::getAllDevicesAndHomes));
    }

    private Route postTemperatureToDevicesRoute() {
        return pathEnd(() -> route(
                    post(() -> (
                            entity(
                                    Jackson.unmarshaller(Temperatures.class),
                                    this::postAllTemperatureToDevices
                            )
                    )),
                    options(() -> complete(StatusCodes.OK, HTTP_HEADERS, "OK", Jackson.marshaller()))
                )
        );
    }

    private Route getAllDevicesAndHomes() {
        CompletionStage<DeviceManager.RespondDevicesInHome> devicesInHome = PatternsCS
                .ask(deviceManager, new DeviceManager.GetDevicesInHomes(), timeout)
                .thenApply(obj ->(DeviceManager.RespondDevicesInHome) obj);

        return onSuccess(() -> devicesInHome,
                performed -> complete(StatusCodes.OK, HTTP_HEADERS, performed.getHomeToDevices(), Jackson.marshaller())
        );
    }

    private Route postAllTemperatureToDevices(Temperatures temperatures) {
        String homeName = temperatures.getHomeName();
        Map<String, Double> deviceToTemperature = temperatures.getDeviceToTemperature();

        CompletionStage<DeviceGroup.HomesWithTemperatureChanged> devicesChanged = PatternsCS
                .ask(deviceManager, new DeviceManager.SetTemperatures(homeName, deviceToTemperature), timeout)
                .thenApply(obj -> (DeviceGroup.HomesWithTemperatureChanged) obj);

        return onSuccess(() -> devicesChanged,
                performed -> complete(StatusCodes.OK, HTTP_HEADERS, performed.getHomesWithTemperatureChanged(), Jackson.marshaller())
        );
    }

    private Route getNumberOfHomesRoute() {
        return pathEnd(() -> get(this::getNumberOfHomes));
    }

    private Route getNumberOfHomes() {
        CompletionStage<DeviceManager.RespondHomesNumber> numberOfHomes = PatternsCS
                .ask(deviceManager, new DeviceManager.GetHomesNumber(), timeout)
                .thenApply(obj ->(DeviceManager.RespondHomesNumber) obj);

        return onSuccess(() -> numberOfHomes,
                performed -> {
                    log.info("Number of homes: " + performed.homesNumber);
                    return complete(StatusCodes.OK, HTTP_HEADERS, performed, Jackson.marshaller());
                }
        );
    }

    private Route homesRoute() {
        return route(
                pathPrefix("home", () -> route(
                        homeRoute(),
                        createHomeRoute()
                ))
        );
    }

    private Route createHomeRoute() {
        return pathEnd(() ->
            post(() ->
                    entity(
                            Jackson.unmarshaller(Home.class),
                            this::validateAndCreateHome
                    )
            )
        );
    }

    private Route homeRoute() {
        return route(
                path(segment().slash("exists"), (homeName) -> get(() -> homeExists(homeName))),
                path(segment().slash("heating"), (homeName) -> route(
                        get(() -> getHeatingSettingsForHome(homeName)),
                        put(() -> alterHeatingSystem(homeName)),
                        options(() -> complete(StatusCodes.OK, HTTP_HEADERS, "OK", Jackson.marshaller()))
                )),
                path(segment().slash("temperature"), (homeName) -> get(() -> getHomeTemperature(homeName))),
                path(segment().slash(segment()), this::addTemperatureDeviceToHome)
        );
    }

    private Route alterHeatingSystem(String homeName) {
        return entity(
                Jackson.unmarshaller(BlockHeating.class),
                (blockHeating) -> changeHeatingSystemSetting(blockHeating, homeName)
        );
    }

    private Route changeHeatingSystemSetting(BlockHeating blockHeating, String homeName) {
        boolean block = blockHeating.isTurnOff();

        PatternsCS.ask(deviceManager, new DeviceManager.ChangeHeatingSettings(homeName, block) , timeout);

        return complete(StatusCodes.OK, HTTP_HEADERS, "Heating system changed", Jackson.marshaller());
    }

    private Route homeExists(String homeName) {
        CompletionStage<Boolean> homeExists = PatternsCS
                .ask(deviceManager, new DeviceManager.IfHomeExists(homeName), timeout)
                .thenApply(obj ->(Boolean) obj);

        return onSuccess(() -> homeExists,
                performed -> complete(StatusCodes.OK, HTTP_HEADERS, performed, Jackson.marshaller()));
    }

    private Route getHeatingSettingsForHome(String homeName) {
        CompletionStage<HeatingSetting> heatingSettings = PatternsCS
                .ask(deviceManager, new DeviceManager.GetHeatingSettings(homeName), timeout)
                .thenApply(obj ->(HeatingSetting) obj);

        return onSuccess(() -> heatingSettings,
                performed -> complete(StatusCodes.OK, HTTP_HEADERS, performed, Jackson.marshaller()));
    }

    private Route addTemperatureDeviceToHome(String home, String deviceName) {
        return post(() -> {
            CompletionStage<DeviceManager.DeviceRegistered> newGroupDevice = PatternsCS
                    .ask(deviceManager, new DeviceManager.RequestTrackDevice(home, deviceName), timeout)
                    .thenApply(obj ->(DeviceManager.DeviceRegistered) obj);

            return onSuccess(() -> newGroupDevice, performed -> complete(StatusCodes.OK, "YAY"))
                    .orElse(complete(StatusCodes.NOT_FOUND, "Not Found"));
        });
    }

    private Route getHomeTemperature(String homeName) {
        CompletionStage<DeviceGroup.RespondAllTemperatures> allTemperatures = PatternsCS
                .ask(deviceManager, new DeviceManager.GetHomeTemperature(homeName), timeout)
                .thenApply(obj ->(DeviceGroup.RespondAllTemperatures) obj);

        return onSuccess(() -> allTemperatures,
                performed -> {
                    log.info("Temperatures in homes: " + performed.getTemperatures());

                    return complete(StatusCodes.OK, HTTP_HEADERS, performed, Jackson.marshaller());
                }
        );
    }

    private Route validateAndCreateHome(Home home) {
        HomeValidator homeValidator = new HomeValidator();

        try {
            homeValidator.validate(home);
        } catch (ValidatorException exc) {
            return complete(StatusCodes.BAD_REQUEST, exc.getMessage());
        }

        return createHome(home);
    }

    private Route createHome(Home home) {
        final String homeName = home.getHomeName();
        final List<String> temperatureDevices = home.getTemperatureDevices();

        CompletionStage<DeviceManager.HomeCreation> newHome = PatternsCS
                .ask(deviceManager,
                        new DeviceManager.CreateHome(homeName, temperatureDevices),
                        timeout)
                .thenApply(obj -> (DeviceManager.HomeCreation) obj);

        return onSuccess(() -> newHome, this::completeHome);
    }

    private Route completeHome(Object performed) {
        if (performed instanceof DeviceManager.HomeCreated) {
            return complete(StatusCodes.OK,
                    "Home " + ((DeviceManager.HomeCreated) performed).homeName + " was created!");
        }

        return complete(StatusCodes.BAD_REQUEST, "Home already exist!");
    }

}
