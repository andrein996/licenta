package server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;

public class IotServer extends AllDirectives {

    // set up ActorSystem and other dependencies here
    private final Routes userRoutes;

    public IotServer(ActorSystem system, ActorRef deviceManager) {
        userRoutes = new Routes(system, deviceManager);
    }

    public Route createRoute() {
        return userRoutes.routes();
    }
}
