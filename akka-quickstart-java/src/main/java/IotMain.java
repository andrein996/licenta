import java.io.IOException;
import java.util.concurrent.CompletionStage;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import manager.DeviceManager;
import server.IotServer;

public class IotMain {

    public static void main(String[] args) throws IOException {
        ActorSystem system = ActorSystem.create("iot-system");

        try {
            final Http http = Http.get(system);
            final ActorMaterializer materializer = ActorMaterializer.create(system);

            ActorRef deviceManager = system.actorOf(DeviceManager.props(), "deviceManager");

            //In order to access all directives we need an instance where the routes are define.
            IotServer app = new IotServer(system, deviceManager);

            final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow(system, materializer);
            final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                    ConnectHttp.toHost("localhost", 8082), materializer);

            System.out.println("IotServer online at http://localhost:8082/\nPress RETURN to stop...");
            System.in.read(); // let it run until user presses return

            binding
                    .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                    .thenAccept(unbound -> system.terminate()); // and shutdown when done
            //#http-server

            System.out.println("Press ENTER to exit the system");
            System.in.read();
        } finally {
            system.terminate();
        }
    }

}
