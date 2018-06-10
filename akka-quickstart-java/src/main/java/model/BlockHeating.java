package model;

public class BlockHeating {

    private boolean turnOff;

    public BlockHeating() {
    }

    public BlockHeating(boolean turnOff) {
        this.turnOff = turnOff;
    }

    public boolean isTurnOff() {
        return turnOff;
    }

    public void setTurnOff(boolean turnOff) {
        this.turnOff = turnOff;
    }
}
