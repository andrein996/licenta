package group.query;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import device.TemperatureDevice;
import group.DeviceGroup;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TemperatureQuery extends AbstractActor {
    private static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String houseName;
    private final Map<ActorRef, String> actorToDeviceId;
    private final ActorRef requester;
    private final ActorRef redirectTo;

    private Cancellable queryTimeoutTimer;

    public TemperatureQuery(String houseName, Map<ActorRef, String> actorToDeviceId, ActorRef requester, ActorRef redirectTo, FiniteDuration timeout) {
        this.houseName = houseName;
        this.actorToDeviceId = actorToDeviceId;
        this.requester = requester;
        this.redirectTo = redirectTo;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
                timeout, getSelf(), new CollectionTimeout(), getContext().dispatcher(), getSelf()
        );
    }

    public static Props props(String houseName, Map<ActorRef, String> actorToDeviceId, ActorRef requester, ActorRef redirectTo, FiniteDuration timeout) {
        return Props.create(TemperatureQuery.class, houseName, actorToDeviceId, requester, redirectTo, timeout);
    }

    @Override
    public void preStart() {
        for (ActorRef deviceActor : actorToDeviceId.keySet()) {
            getContext().watch(deviceActor);
            deviceActor.tell(new TemperatureDevice.ReadTemperature(0L), getSelf());
        }
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
    }

    private Receive waitingForReplies(
            Map<String, Double> repliesSoFar,
            Set<ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(TemperatureDevice.RespondTemperature.class,
                        r -> respondToTemperatureUpdate(r,
                                stillWaiting,
                                repliesSoFar)
                )
                .match(Terminated.class,
                        t -> receivedResponse(t.getActor(),
                                null,
                                stillWaiting,
                                repliesSoFar)
                )
                .match(CollectionTimeout.class,
                        t -> respondToCollectionTimeout(stillWaiting, repliesSoFar)
                )
                .build();
    }

    private void respondToCollectionTimeout(Set<ActorRef> stillWaiting,
                                            Map<String, Double> repliesSoFar) {
        Map<String, Double> replies = new HashMap<>(repliesSoFar);
        for (ActorRef deviceActor : stillWaiting) {
            String deviceId = actorToDeviceId.get(deviceActor);
            replies.put(deviceId, null);
        }
        requester.tell(new DeviceGroup.RespondAllTemperatures(houseName, replies), getSelf());
        getContext().stop(getSelf());
    }

    private void respondToTemperatureUpdate(TemperatureDevice.RespondTemperature response,
                                            Set<ActorRef> stillWaiting,
                                            Map<String, Double> repliesSoFar) {
        ActorRef deviceActor = getSender();
        Double responseValue = response.value;

        receivedResponse(deviceActor, responseValue, stillWaiting, repliesSoFar);
    }

    private void receivedResponse(ActorRef deviceActor,
                                 Double reading,
                                 Set<ActorRef> stillWaiting,
                                 Map<String, Double> repliesSoFar) {
        getContext().unwatch(deviceActor);
        String deviceId = actorToDeviceId.get(deviceActor);

        Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
        newStillWaiting.remove(deviceActor);

        Map<String, Double> newRepliesSoFar = new HashMap<>(repliesSoFar);
        newRepliesSoFar.put(deviceId, reading);
        if (newStillWaiting.isEmpty()) {
            if (redirectTo == null) {
                requester.tell(new DeviceGroup.RespondAllTemperatures(houseName, newRepliesSoFar), getSelf());
            } else {
                requester.tell(new DeviceGroup.RespondAllTemperaturesToActor(houseName, newRepliesSoFar, redirectTo), getSelf());
            }
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}
