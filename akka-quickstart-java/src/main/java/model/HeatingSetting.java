package model;

import group.enums.HeatingSettings;

public class HeatingSetting {

    private final String setting;
    private final Double value;
    private final boolean userTurnedOff;

    public HeatingSetting(HeatingSettings heatingSettings, boolean userTurnedOff) {
        this.setting = heatingSettings.getSetting();
        this.value = heatingSettings.getValue();
        this.userTurnedOff = userTurnedOff;
    }

    public String getSetting() {
        return setting;
    }

    public Double getValue() {
        return value;
    }

    public boolean isUserTurnedOff() {
        return userTurnedOff;
    }
}
