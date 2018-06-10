package device;

import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import device.temperature.RecordTemperature;
import manager.DeviceManager;

public class TemperatureDevice extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String groupId;

    private final String deviceId;

    public TemperatureDevice(String groupId, String deviceId) {
        this.groupId = groupId;
        this.deviceId = deviceId;
    }

    public static Props props(String groupId, String deviceId) {
        return Props.create(TemperatureDevice.class, groupId, deviceId);
    }

    public static final class TemperatureRecorded {
        private final String deviceId;

        public TemperatureRecorded(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceId() {
            return deviceId;
        }
    }


    public static final class ReadTemperature {
        public long requestId;

        public ReadTemperature(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class RespondTemperature {
        private String houseName;
        long requestId;
        public Double value;

        public RespondTemperature(String houseName, long requestId, Double value) {
            this.houseName = houseName;
            this.requestId = requestId;
            this.value = value;
        }

        public String getHouseName() {
            return houseName;
        }
    }

    private Double lastTemperatureReading = 25.0;

    @Override
    public void preStart() {
        log.info("Device actor {}-{} started", groupId, deviceId);
    }

    @Override
    public void postStop() {
        log.info("Device actor {}-{} stopped", groupId, deviceId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceManager.RequestTrackDevice.class, r -> {
                    if (this.groupId.equals(r.groupId) && this.deviceId.equals(r.deviceId)) {
                        getSender().tell(new DeviceManager.DeviceRegistered(r.deviceId), getSelf());
                    } else {
                        log.warning(
                                "Ignoring TrackDevice request for {}-{}.This actor is responsible for {}-{}.",
                                r.groupId, r.deviceId, this.groupId, this.deviceId
                        );
                    }
                })
                .match(RecordTemperature.class, r -> {
                    lastTemperatureReading += r.getValue();
                    getSender().tell(new TemperatureRecorded(deviceId), getSelf());
                })
                .match(ReadTemperature.class, r -> {
                    getSender().tell(new RespondTemperature(this.groupId, r.requestId, lastTemperatureReading), getSelf());
                })
                .build();
    }

}
