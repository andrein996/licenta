package group;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import device.TemperatureDevice;
import group.enums.HeatingSettings;
import group.query.AddTemperatureQuery;
import group.query.TemperatureQuery;
import manager.DeviceManager;
import model.HeatingSetting;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DeviceGroup extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;
    private HeatingSettings heatingSettings = HeatingSettings.OFF;
    private boolean blockHeating = false;

    public DeviceGroup(String groupId) {
        this.groupId = groupId;
    }

    public static Props props(String groupId) {
        return Props.create(DeviceGroup.class, groupId);
    }

    public static final class RequestDeviceList {
    }

    public static final class AddTemperatures {
        private final Map<String, Double> deviceToTemperature;

        public AddTemperatures(Map<String, Double> deviceToTemperature) {
            this.deviceToTemperature = deviceToTemperature;
        }

        public Map<String, Double> getDeviceToTemperature() {
            return deviceToTemperature;
        }
    }

    public static final class ReplyDeviceList {
        final String houseName;
        final Set<String> ids;

        public ReplyDeviceList(String houseName, Set<String> ids) {
            this.houseName = houseName;
            this.ids = ids;
        }

        public String getHouseName() {
            return houseName;
        }

        public Set<String> getIds() {
            return ids;
        }
    }

    public static final class HomesWithTemperatureChanged {
        private final List<String> homesWithTemperatureChanged;

        public HomesWithTemperatureChanged(List<String> homesWithTemperatureChanged) {
            this.homesWithTemperatureChanged = homesWithTemperatureChanged;
        }

        public List<String> getHomesWithTemperatureChanged() {
            return homesWithTemperatureChanged;
        }
    }

    public static class RespondAllTemperaturesToActor extends RespondAllTemperatures {
        private final ActorRef answearTo;

        public RespondAllTemperaturesToActor(String houseName, Map<String, Double> temperatures, ActorRef answearTo) {
            super(houseName, temperatures);
            this.answearTo = answearTo;
        }

        public ActorRef getAnswearTo() {
            return answearTo;
        }
    }

    public static class RespondAllTemperatures {
        private final String houseName;
        private final Map<String, Double> temperatures;

        public RespondAllTemperatures(String houseName, Map<String, Double> temperatures) {
            this.houseName = houseName;
            this.temperatures = temperatures;
        }

        public String getHouseName() {
            return houseName;
        }

        public Map<String, Double> getTemperatures() {
            return temperatures;
        }
    }

    public interface TemperatureReading {
    }

    public static final class Temperature implements TemperatureReading {
        public final double value;

        public Temperature(double value) {
            this.value = value;
        }
    }

    public static final class TemperatureNotAvailable implements TemperatureReading {
    }

    public static final class DeviceNotAvailable implements TemperatureReading {
    }

    public static final class DeviceTimedOut implements TemperatureReading {
    }

    private final Map<String, ActorRef> deviceIdToActor = new HashMap<>();
    private final Map<ActorRef, String> actorToDeviceId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("DeviceGroup {} started", groupId);
    }

    @Override
    public void postStop() {
        log.info("DeviceGroup {} stopped", groupId);
    }

    private void onTrackDevice(DeviceManager.RequestTrackDevice trackMsg) {
        if (this.groupId.equals(trackMsg.groupId)) {
            ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);

            createDeviceActor(trackMsg, deviceActor);
        } else {
            log.warning(
                    "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
                    groupId, this.groupId
            );
        }
    }

    private void createDeviceActor(DeviceManager.RequestTrackDevice trackMsg, ActorRef deviceActor) {
        if (deviceActor != null) {
            deviceActor.forward(trackMsg, getContext());
        } else {
            log.info("Creating device actor for {}", trackMsg.deviceId);
            deviceActor = getContext().actorOf(TemperatureDevice.props(groupId, trackMsg.deviceId), trackMsg.deviceId);
            getContext().watch(deviceActor);
            actorToDeviceId.put(deviceActor, trackMsg.deviceId);
            deviceIdToActor.put(trackMsg.deviceId, deviceActor);
            deviceActor.forward(trackMsg, getContext());
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef deviceActor = t.getActor();
        String deviceId = actorToDeviceId.get(deviceActor);
        log.info("Device actor for {} has been terminated", deviceId);
        actorToDeviceId.remove(deviceActor);
        deviceIdToActor.remove(deviceId);
    }

    private void onAllTemperatures() {
        Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorToDeviceId);

        getContext().actorOf(TemperatureQuery.props(
                groupId, actorToDeviceIdCopy, getSender(), null, new FiniteDuration(3, TimeUnit.SECONDS)));
    }

    private double calculateAverageTemperature(Map<String, Double> temperatures) {
        return temperatures
                .values()
                .stream()
                .mapToDouble(value -> value)
                .average().orElse(Double.MAX_VALUE);
    }

    private void changeHeatingSettings(RespondAllTemperaturesToActor allTemperatures) {
        if (blockHeating) {
            return;
        }

        double averageTemperature = calculateAverageTemperature(allTemperatures.getTemperatures());

        if (averageTemperature <= 15) {
            this.heatingSettings = HeatingSettings.VERY_HIGH;
        } else if (averageTemperature <= 18) {
            this.heatingSettings = HeatingSettings.HIGH;
        } else if (averageTemperature <= 23) {
            this.heatingSettings = HeatingSettings.MEDIUM;
        } else if (averageTemperature >= 28) {
            this.heatingSettings = HeatingSettings.LOW;
        } else if (averageTemperature >= 32) {
            this.heatingSettings = HeatingSettings.VERY_LOW;
        } else {
            this.heatingSettings = HeatingSettings.OFF;
        }

        log.info("New heating setting is: {} for house {}. Avg. temp: {}",
                this.heatingSettings.getSetting(), this.groupId, averageTemperature
        );

        allTemperatures
                .getAnswearTo()
                .tell(
                        new RespondAllTemperatures(allTemperatures.getHouseName(),
                                allTemperatures.getTemperatures()),
                        getSelf());
    }

    private void onAlterHeatingSettings() {
        Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorToDeviceId);

        getContext().actorOf(TemperatureQuery.props(
                groupId, actorToDeviceIdCopy, getSelf(), getSender(), new FiniteDuration(3, TimeUnit.SECONDS)));
    }

    private void onAddTemperatures(AddTemperatures addTemperatures) {
        final Map<String, Double> temperatures = addTemperatures.getDeviceToTemperature();

        //changeHeatingSettings(temperatures);

        final Map<String, ActorRef> devicesWithNewTemperatures = temperatures
                .keySet()
                .stream()
                .filter(temperatures::containsKey)
                .map(deviceName -> new AbstractMap.SimpleEntry<String, ActorRef>(deviceName, deviceIdToActor.get(deviceName)) {
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        //Altering temperatures based on heatingSettings
        final Set<String> keys = temperatures.keySet();

        for (String key : keys) {
            temperatures.replace(key, temperatures.get(key) + this.heatingSettings.getValue());
        }

        getContext().actorOf(AddTemperatureQuery.props(
                devicesWithNewTemperatures, temperatures, getSender(), new FiniteDuration(3, TimeUnit.SECONDS)));
    }

    private void onGetHeatingSettings() {
        getSender().tell(new HeatingSetting(this.heatingSettings, blockHeating), getSelf());
    }

    private void onChangeHeatingSettings(DeviceManager.ChangeHeatingSettings changeHeatingSettings) {
        boolean blockHeating = changeHeatingSettings.isBlockHeating();
        this.blockHeating = blockHeating;

        if (blockHeating) {
            this.heatingSettings = HeatingSettings.OFF;
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceManager.RequestTrackDevice.class, this::onTrackDevice)
                .match(RequestDeviceList.class, r -> onAllTemperatures())
                .match(Terminated.class, this::onTerminated)
                .match(DeviceManager.GetHomeTemperature.class, r -> onAllTemperatures())
                .match(AddTemperatures.class, this::onAddTemperatures)
                .match(DeviceManager.AlterHeatingSettings.class, r -> onAlterHeatingSettings())
                .match(RespondAllTemperaturesToActor.class, this::changeHeatingSettings)
                .match(DeviceManager.GetHeatingSettings.class, r -> onGetHeatingSettings())
                .match(DeviceManager.ChangeHeatingSettings.class, this::onChangeHeatingSettings)
                .build();
    }
}
