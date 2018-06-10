require("./stylesheet.scss");

import React, {Component} from 'react';

const returnNumberToFixedDigits = (number, digits) => (number.toFixed(digits));

export default class Temperature extends Component {

    constructor(props) {
        super(props);

        this.state = {
            deviceName: props.deviceName,
            currentTemperature: props.currentTemperature ? props.currentTemperature : "No data"
        };
    }

    componentWillReceiveProps(nextProps) {
        const nextTemperature = nextProps.currentTemperature;

        if (nextProps.isFetched) {
            this.setState({
                currentTemperature: parseFloat(nextTemperature)
            });
        }
    }

    render () {
        return (
            <div className="device">
                <span className="device-name">{this.state.deviceName}</span>

                <div className="device-temperature">
                    <div className="current-temperature">
                        <span className="sent-temperature-text">Sent temperature: </span>
                        <span>{returnNumberToFixedDigits(this.state.currentTemperature, 3)}</span>
                    </div>
                </div>
            </div>
        );
    }
}