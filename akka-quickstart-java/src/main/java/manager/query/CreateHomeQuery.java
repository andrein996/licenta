package manager.query;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import manager.DeviceManager;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;

public class CreateHomeQuery extends AbstractActor {
    public static final class CollectionTimeout {
    }

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String homeName;
    private final ActorRef homeActor;
    private final List<String> temperatureDevices;
    private final ActorRef requester;

    private Cancellable queryTimeoutTimer;

    public CreateHomeQuery(String homeName,
                               ActorRef homeActor,
                               List<String> temperatureDevices,
                               ActorRef requester,
                               FiniteDuration timeout) {
        this.homeName = homeName;
        this.homeActor = homeActor;
        this.temperatureDevices = temperatureDevices;
        this.requester = requester;

        queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(
                timeout, getSelf(), new CreateHomeQuery.CollectionTimeout(), getContext().dispatcher(), getSelf()
        );
    }

    public static Props props(String homeName,
                              ActorRef homeActor,
                              List<String> temperatureDevices,
                              ActorRef requester,
                              FiniteDuration timeout) {
        return Props.create(CreateHomeQuery.class, homeName, homeActor, temperatureDevices, requester, timeout);
    }

    @Override
    public void preStart() {
        temperatureDevices.forEach(temperatureDeviceId -> {
            homeActor.tell(new DeviceManager.RequestTrackDevice(homeName, temperatureDeviceId), getSelf());
        });
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new ArrayList<>(), temperatureDevices);
    }

    private Receive waitingForReplies(List<String> repliesSoFar, List<String> stillWaiting) {
        return receiveBuilder()
                .match(DeviceManager.DeviceRegistered.class, r ->
                    receivedResponse(r.deviceName, repliesSoFar, stillWaiting)
                )
                .match(CollectionTimeout.class, t -> {
                    List<String> stillWaitingForReplies = new ArrayList<>(stillWaiting);

                    requester.tell(new DeviceManager.HomeCreated(homeName, stillWaitingForReplies), getSelf());

                    getContext().stop(getSelf());
                })
                .build();
    }

    private void receivedResponse(String deviceName,
                                 List<String> repliesSoFar,
                                 List<String> stillWaiting) {
        List<String> newStillWaiting = new ArrayList<>(stillWaiting);
        newStillWaiting.remove(deviceName);

        List<String> newRepliesSoFar = new ArrayList<>(repliesSoFar);
        newRepliesSoFar.add(deviceName);

        if (newStillWaiting.isEmpty()) {
            requester.tell(new DeviceManager.HomeCreated(homeName, newRepliesSoFar), getSelf());
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}
