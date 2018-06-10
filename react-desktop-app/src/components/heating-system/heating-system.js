require("./stylesheet.scss");

import React, {Component} from 'react';
import {fetchData, fetchDataPut} from "../../networking/networking";

const createHeatingSystem = (text, className) => (
    <div className={`heating-system-setting settings-${className}`}>
        <div className="heating-system-settings-text">
            {text}
        </div>
    </div>
);

const createButton = (text, className, onClick, onClickValue) => (
    <div
        className={`heating-system-button ${className}`}
        onClick={() => onClick(onClickValue)}>
            {text}
    </div>
);

const getHeatingSetting = (data, isTurnedOff) => {
    const value = data ? data.value : null;
    let heatingSystem = createHeatingSystem("NO DATA", "off");

    if (data && isTurnedOff) {
        heatingSystem = createHeatingSystem("TURNED OFF", "off");
    } else if (value < 0) {
        heatingSystem = createHeatingSystem(data.setting, "decreasing");
    } else if (value > 0) {
        heatingSystem = createHeatingSystem(data.setting, "increasing");
    } else if (value === 0) {
        heatingSystem = createHeatingSystem(data.setting, "off");
    }

    return heatingSystem;
};

const getButtonState = (isUserTurnedOff, onClick) => {
    if (isUserTurnedOff) {
        return createButton("TURN ON", "turn-on", onClick, false);
    } else if (isUserTurnedOff === false) {
        return createButton("TURN OFF", "turn-off", onClick, true);
    }

    return createButton("DISABLED", "disabled", onClick, null);
};

export default class HeatingSystem extends Component {

    constructor(props) {
        super(props);

        this.state = {
            homeName: props.homeName,
            heatingSetting: null,
            isTurnedOffByUser: null
        };

        this.switchButton = this.switchButton.bind(this);
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    componentDidMount() {
        this.fetchHeatingSettings();

        const intervalId = setInterval(() => this.fetchHeatingSettings(), 1000 * 6);
        // store intervalId in the state so it can be accessed later:
        this.setState({
            intervalId: intervalId
        });
    }

    fetchHeatingSettings() {
        fetchData(`api/home/${this.state.homeName}/heating`)
            .then(data => this.setState({
                heatingSetting: data,
                isTurnedOffByUser: data.userTurnedOff
            }))
    }

    switchButton(turnOff) {
        if (turnOff !== null) {
            this.setState({isTurnedOffByUser: turnOff});
        }

        const bodyToSend = {
            turnOff: turnOff
        };

        fetchDataPut(`api/home/${this.state.homeName}/heating`, bodyToSend);
    }

    render () {
        return (
            <div className="heating-system">
                <span>Settings</span>
                <div className="heating-system-settings">
                    {getHeatingSetting(this.state.heatingSetting, this.state.isTurnedOffByUser)}
                    {getButtonState(this.state.isTurnedOffByUser, this.switchButton)}
                </div>
            </div>
        );
    }
}