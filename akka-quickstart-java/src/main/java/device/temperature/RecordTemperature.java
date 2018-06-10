package device.temperature;

public final class RecordTemperature {
    private final double value;
    private final String deviceName;

    public RecordTemperature(double value, String deviceName) {
        this.value = value;
        this.deviceName = deviceName;
    }

    public double getValue() {
        return value;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
