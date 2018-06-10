package validator;

import model.Home;

import java.util.List;

public class HomeValidator implements IValidator<Home> {

    @Override
    public void validate(Home entity) throws ValidatorException {
        final String homeName = entity.getHomeName();
        final List<String> temperatureDevices = entity.getTemperatureDevices();

        if (!isValidActor(homeName) || !areValidActors(temperatureDevices)) {
            throw new ValidatorException("Home name and temperature devices should be at least 3 characters long, " +
                    "not start with $ and contain no space.");
        }
    }

    private boolean isValidActor(String actor) {
        return !containsSpacesOrStartsWithDollar(actor)
                && !lengthLessThan3(actor);
    }

    private boolean areValidActors(List<String> stringList) {
        return stringList
                .stream()
                .filter(this::isValidActor)
                .findAny()
                .isPresent();
    }

    private boolean lengthLessThan3(String string) {
        return string.length() < 3;
    }

    private boolean containsSpacesOrStartsWithDollar(String string) {
        return string.charAt(0) == '$' || string.contains(" ");
    }
}
