package manager.query;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import group.DeviceGroup;
import manager.DeviceManager;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.stream.Collectors;

public class GetAllDevicesInHomesQuery extends AbstractActor {
    public static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final Map<String, ActorRef> homeNameToActors;
    private final Map<ActorRef, String> actorsToHomeName;
    private final ActorRef requester;

    private Cancellable queryTimeoutTimer;

    public GetAllDevicesInHomesQuery(Map<String, ActorRef> homeNameToActors,
                                     Map<ActorRef, String> actorsToHomeName,
                                     ActorRef requester,
                                     FiniteDuration timeout) {
        this.homeNameToActors = homeNameToActors;
        this.actorsToHomeName = actorsToHomeName;
        this.requester = requester;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
                timeout, getSelf(), new GetAllDevicesInHomesQuery.CollectionTimeout(), getContext().dispatcher(), getSelf()
        );
    }

    public static Props props(Map<String, ActorRef> homeNameToActors,
                              Map<ActorRef, String> actorsToHomeName,
                              ActorRef requester,
                              FiniteDuration timeout) {
        return Props.create(GetAllDevicesInHomesQuery.class, homeNameToActors, actorsToHomeName, requester, timeout);
    }

    @Override
    public void preStart() {
        actorsToHomeName.keySet()
                .forEach(homeActor -> homeActor.tell(new DeviceGroup.RequestDeviceList(), getSelf()));
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), homeNameToActors);
    }

    private Receive waitingForReplies(Map<String, List<Map.Entry<String, Double>>> repliesSoFar, Map<String, ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(DeviceGroup.RespondAllTemperatures.class, r ->
                        receivedResponse(r.getHouseName(), r.getTemperatures(), repliesSoFar, stillWaiting)
                )
                .match(CreateHomeQuery.CollectionTimeout.class, t -> {
                    requester.tell(new DeviceManager.RespondDevicesInHome(repliesSoFar), getSelf());

                    getContext().stop(getSelf());
                })
                .build();
    }

    private Map.Entry<String, Double> getReplyEntry(String deviceName, Double temperature) {
        return new AbstractMap.SimpleEntry<>(deviceName, temperature);
    }

    private List<Map.Entry<String, Double>> getReplyList(Map<String, Double> devices) {
        return devices
                .keySet()
                .stream()
                .sorted()
                .map(deviceName -> getReplyEntry(deviceName, devices.get(deviceName)))
                .collect(Collectors.toList());
    }

    private void receivedResponse(String houseName,
                                  Map<String, Double> devices,
                                  Map<String, List<Map.Entry<String, Double>>> repliesSoFar,
                                  Map<String, ActorRef> stillWaiting) {
        Map<String, ActorRef> newStillWaiting = new HashMap<>(stillWaiting);
        ActorRef responded = newStillWaiting.remove(houseName);

        getContext().unwatch(responded);

        Map<String, List<Map.Entry<String, Double>>> newRepliesSoFar = new HashMap<>(repliesSoFar);
        newRepliesSoFar.put(houseName, getReplyList(devices));

        if (newStillWaiting.isEmpty()) {
            requester.tell(new DeviceManager.RespondDevicesInHome(newRepliesSoFar), getSelf());
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}


