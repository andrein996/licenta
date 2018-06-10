package model;

import java.util.HashMap;
import java.util.Map;

public class Temperatures {

    private String homeName;
    private Map<String, Double> deviceToTemperature;

    public Temperatures() {
        this.homeName = "";
        this.deviceToTemperature = new HashMap<>();
    }

    public Temperatures(String homeName, Map<String, Double> deviceToTemperature) {
        this.homeName = homeName;
        this.deviceToTemperature = deviceToTemperature;
    }

    public String getHomeName() {
        return homeName;
    }

    public void setHomeName(String homeName) {
        this.homeName = homeName;
    }

    public Map<String, Double> getDeviceToTemperature() {
        return deviceToTemperature;
    }

    public void setDeviceToTemperature(Map<String, Double> deviceToTemperature) {
        this.deviceToTemperature = deviceToTemperature;
    }
}
