package group.query;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import device.TemperatureDevice;
import device.temperature.RecordTemperature;
import group.DeviceGroup;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddTemperatureQuery extends AbstractActor {
    public static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final Map<String, ActorRef> deviceNameToActor;
    private final Map<String, Double> temperatures;
    private final ActorRef requester;

    private Cancellable queryTimeoutTimer;

    public AddTemperatureQuery(Map<String, ActorRef> deviceNameToActor,
                               Map<String, Double> temperatures,
                               ActorRef requester,
                               FiniteDuration timeout) {
        this.deviceNameToActor = deviceNameToActor;
        this.temperatures = temperatures;
        this.requester = requester;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
                timeout, getSelf(), new AddTemperatureQuery.CollectionTimeout(), getContext().dispatcher(), getSelf()
        );
    }

    public static Props props(Map<String, ActorRef> deviceNameToActor,
                              Map<String, Double> temperatures,
                              ActorRef requester,
                              FiniteDuration timeout) {
        return Props.create(AddTemperatureQuery.class, deviceNameToActor, temperatures, requester, timeout);
    }

    @Override
    public void preStart() {
        deviceNameToActor.forEach((deviceName, actorRef) -> {
            Double temperature = temperatures.get(deviceName);

            actorRef.tell(new RecordTemperature(temperature, deviceName), getSelf());
        });
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new ArrayList<>(), deviceNameToActor);
    }

    private Receive waitingForReplies(List<String> repliesSoFar, Map<String, ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(TemperatureDevice.TemperatureRecorded.class, r ->
                        receivedResponse(r.getDeviceId(), repliesSoFar, stillWaiting)
                )
                .match(CollectionTimeout.class, t -> {
                    requester.tell(new DeviceGroup.HomesWithTemperatureChanged(repliesSoFar), getSelf());

                    getContext().stop(getSelf());
                })
                .build();
    }

    private void receivedResponse(String deviceName,
                                  List<String> repliesSoFar,
                                  Map<String, ActorRef> stillWaiting) {
        Map<String, ActorRef> newStillWaiting = new HashMap<>(stillWaiting);
        ActorRef responded = newStillWaiting.remove(deviceName);

        getContext().unwatch(responded);

        List<String> newRepliesSoFar = new ArrayList<>(repliesSoFar);
        newRepliesSoFar.add(deviceName);

        if (stillWaiting.isEmpty()) {
            requester.tell(new DeviceGroup.HomesWithTemperatureChanged(newRepliesSoFar), getSelf());

            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}