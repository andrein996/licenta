package manager;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import group.DeviceGroup;
import group.enums.HeatingSettings;
import manager.query.CreateHomeQuery;
import manager.query.GetAllDevicesInHomesQuery;
import model.HeatingSetting;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeviceManager extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(DeviceManager.class);
    }

    public static final class RequestTrackDevice {
        public final String groupId;
        public final String deviceId;

        public RequestTrackDevice(String groupId, String deviceId) {
            this.groupId = groupId;
            this.deviceId = deviceId;
        }
    }

    public static final class AlterHeatingSettings {
    }

    public static final class ChangeHeatingSettings {
        private String homeName;
        private boolean blockHeating;

        public ChangeHeatingSettings(String homeName, boolean blockHeating) {
            this.homeName = homeName;
            this.blockHeating = blockHeating;
        }

        public String getHomeName() {
            return homeName;
        }

        public boolean isBlockHeating() {
            return blockHeating;
        }
    }

    public static final class GetHeatingSettings {
        private final String homeName;

        public GetHeatingSettings(String homeName) {
            this.homeName = homeName;
        }
    }

    public static final class GetHomesNumber {
    }

    public static final class IfHomeExists {
        private final String homeName;

        public IfHomeExists(String homeName) {
            this.homeName = homeName;
        }

        public String getHomeName() {
            return homeName;
        }
    }

    public static final class GetDevicesInHomes {
    }

    public static final class RespondDevicesInHome {
        private final Map<String, List<Map.Entry<String, Double>>> homeToDevices;

        public RespondDevicesInHome(Map<String, List<Map.Entry<String, Double>>> homeToDevices) {
            this.homeToDevices = homeToDevices;
        }

        public Map<String, List<Map.Entry<String, Double>>> getHomeToDevices() {
            return homeToDevices;
        }
    }

    public static final class RespondHomesNumber {
        public final Integer homesNumber;

        RespondHomesNumber(Integer homesNumber) {
            this.homesNumber = homesNumber;
        }
    }

    public interface HomeCreation {

    }

    public static final class HomeCreated implements HomeCreation {
        public final String homeName;
        final List<String> temperatureDevices;

        public HomeCreated(String homeName, List<String> temperatureDevices) {
            this.homeName = homeName;
            this.temperatureDevices = temperatureDevices;
        }
    }

    public static final class HomeAlreadyExists implements HomeCreation {
    }

    public static final class DeviceRegistered {
        public final String deviceName;

        public DeviceRegistered(String deviceName) {
            this.deviceName = deviceName;
        }
    }

    public static final class CreateHome {
        private final String homeName;
        private final List<String> temperatureDevices;

        public CreateHome(String homeName, List<String> temperatureDevices) {
            this.homeName = homeName;
            this.temperatureDevices = temperatureDevices;
        }

        public String getHomeName() {
            return homeName;
        }

        public List<String> getTemperatureDevices() {
            return temperatureDevices;
        }
    }

    public static final class SetTemperatures {
        private final String homeName;
        private final Map<String, Double> deviceToTemperature;

        public SetTemperatures(String homeName, Map<String, Double> deviceToTemperature) {
            this.homeName = homeName;
            this.deviceToTemperature = deviceToTemperature;
        }

        public String getHomeName() {
            return homeName;
        }

        public Map<String, Double> getDeviceToTemperature() {
            return deviceToTemperature;
        }
    }

    public static final class GetHomeTemperature {
        private final String homeName;

        public GetHomeTemperature(String homeName) {
            this.homeName = homeName;
        }
    }

    final Map<String, ActorRef> groupIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToGroupId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("DeviceManager started");
    }

    @Override
    public void postStop() {
        log.info("DeviceManager stopped");
    }

    private void onTrackDevice(RequestTrackDevice trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.forward(trackMsg, getContext());
        } else {
            log.info("Creating home actor for {}", groupId);
            ActorRef homeActor = createHome(groupId);
            homeActor.forward(trackMsg, getContext());
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef groupActor = t.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log.info("Home {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }

    private void onGetHomesNumber() {
        getSender().tell(new RespondHomesNumber(groupIdToActor.size()), getSelf());
    }

    private ActorRef createHome(String homeName) {
        ActorRef groupActor = getContext().actorOf(DeviceGroup.props(homeName), homeName);
        getContext().watch(groupActor);
        groupIdToActor.put(homeName, groupActor);
        actorToGroupId.put(groupActor, homeName);

        return groupActor;
    }

    private void onCreateHome(CreateHome home) {
        final String homeName = home.getHomeName();
        final List<String> homeDevices = home.getTemperatureDevices();

        ActorRef ref = groupIdToActor.get(homeName);

        if (ref != null) {
            getSender().tell(new HomeAlreadyExists(), getSelf());
        } else {
            log.info("Creating home actor for {}", homeName);

            ActorRef homeActor = createHome(homeName);
            getContext().actorOf(CreateHomeQuery.props(homeName,
                    homeActor,
                    homeDevices,
                    getSender(),
                    new FiniteDuration(3, TimeUnit.SECONDS)));
        }
    }

    private void onGetHomeTemperature(GetHomeTemperature home) {
        final String homeName = home.homeName;

        ActorRef ref = groupIdToActor.get(homeName);

        if (ref != null) {
            log.info("Getting temperature for home {}", homeName);

            ref.forward(home, getContext());
        } else {
            getSender().tell(new DeviceGroup.RespondAllTemperatures(null, null), getSelf());
        }
    }

    private void onGetDevicesInHomes() {
        if (groupIdToActor.isEmpty()) {
            getSender().tell(new RespondDevicesInHome(null), getSelf());
        }

        getContext().actorOf(GetAllDevicesInHomesQuery.props(groupIdToActor,
                actorToGroupId,
                getSender(),
                new FiniteDuration(3, TimeUnit.SECONDS)));
    }

    private void onSetTemperatures(SetTemperatures ref) {
        final String homeName = ref.getHomeName();
        final Map<String, Double> deviceToTemperature = ref.getDeviceToTemperature();

        ActorRef homeActor = groupIdToActor.get(homeName);

        if (homeActor != null) {
            homeActor.tell(new DeviceGroup.AddTemperatures(deviceToTemperature), getSender());
        }
    }

    private void onAlterHeatingSystem() {
        actorToGroupId
                .keySet()
                .forEach(actorRef -> actorRef.tell(new AlterHeatingSettings(), getSelf()));
    }

    private void onRespondAllTemperatures(DeviceGroup.RespondAllTemperatures allTemperatures) {
        log.info("Heating settings changed for home: {} with success", allTemperatures.getHouseName());
    }

    private void onIfHomeExists(IfHomeExists ifHomeExists) {
        final String homeName = ifHomeExists.getHomeName();

        final Boolean homeExists = groupIdToActor.containsKey(homeName);

        getSender().tell(homeExists, getSelf());
    }

    private void onGetHeatingSettings(GetHeatingSettings getHeatingSettings) {
        final String homeName = getHeatingSettings.homeName;

        ActorRef home = groupIdToActor.get(homeName);

        if (home != null) {
            home.forward(getHeatingSettings, getContext());
        } else {
            getSender().tell(new HeatingSetting(HeatingSettings.OFF, true), getSelf());
        }
    }

    private void onChangeHeatingSettings(ChangeHeatingSettings changeHeatingSettings) {
        final String homeName = changeHeatingSettings.homeName;

        ActorRef home = groupIdToActor.get(homeName);

        if (home != null) {
            home.forward(changeHeatingSettings, getContext());
        }
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestTrackDevice.class, this::onTrackDevice)
                .match(Terminated.class, this::onTerminated)
                .match(GetHomesNumber.class, request -> onGetHomesNumber())
                .match(CreateHome.class, this::onCreateHome)
                .match(SetTemperatures.class, this::onSetTemperatures)
                .match(GetHomeTemperature.class, this::onGetHomeTemperature)
                .match(GetDevicesInHomes.class, request -> onGetDevicesInHomes())
                .match(AlterHeatingSettings.class, r -> onAlterHeatingSystem())
                .match(DeviceGroup.RespondAllTemperatures.class, this::onRespondAllTemperatures)
                .match(IfHomeExists.class, this::onIfHomeExists)
                .match(GetHeatingSettings.class, this::onGetHeatingSettings)
                .match(ChangeHeatingSettings.class, this::onChangeHeatingSettings)
                .build();
    }

}