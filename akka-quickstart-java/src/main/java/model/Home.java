package model;

import java.util.ArrayList;
import java.util.List;

public class Home {

    private String homeName;
    private List<String> temperatureDevices;

    public Home() {
        homeName = "";
        temperatureDevices = new ArrayList<>();
    }

    public Home(String homeName, List<String> temperatureDevices) {
        this.homeName = homeName;
        this.temperatureDevices = temperatureDevices;
    }

    public String getHomeName() {
        return homeName;
    }

    public void setHomeName(String homeName) {
        this.homeName = homeName;
    }

    public List<String> getTemperatureDevices() {
        return temperatureDevices;
    }

    public void setTemperatureDevices(List<String> temperatureDevices) {
        this.temperatureDevices = temperatureDevices;
    }
}
