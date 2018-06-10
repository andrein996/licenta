package group.enums;

public enum HeatingSettings {
    VERY_HIGH(2.0, "Increasing temperature"),
    HIGH(1.5, "Increasing temperature"),
    MEDIUM(0.5, "Increasing temperature"),
    LOW(-0.5, "Decreasing temperature"),
    VERY_LOW(-1.0, "Decreasing temperature"),
    OFF(0, "OFF");

    private final double value;
    private final String setting;
    HeatingSettings(double value, String setting) {
        this.value = value;
        this.setting = setting;
    }
    public double getValue() { return value; }
    public String getSetting() { return setting; }
}
