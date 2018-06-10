require("./stylesheet.scss");

import React, {Component} from 'react';
import Temperature from "./../temperature/temperature"
import {fetchDataPostWithBody} from './../../networking/networking';

const temperatureDevice = (device, isFetched) => {
    const temperatureObject = getTemperatureObject(device);

    return temperatureObject ?
        (<Temperature
            deviceName={temperatureObject.deviceName}
            currentTemperature={temperatureObject.temperature}
            isFetched={isFetched}
        />) : null;
};

const getTemperatureObject = (device) => {
    const key = Object.keys(device);

    if (key.length !== 1) {
        return null;
    }

    const readTemperature = device[key[0]];

    return ({
        deviceName: key[0],
        temperature: readTemperature
    });
};

const generateTemperature = () => (
    ((Math.random() * 7) - 3.5).toFixed(2)
);

export default class House extends Component {

    constructor(props) {
        super(props);

        this.state = {
            name: props.name,
            devices: props.devices,
            isFetched: false
        };
    }

    componentDidMount() {
        const intervalId = setInterval(() => this.timer(this.state), 1000 * 3);
        // store intervalId in the state so it can be accessed later:
        this.setState({
            intervalId: intervalId,
            isFetched: false
        });
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    timer(home) {
        const cloneDevices = home.devices.slice();
        const homeName = home.name;
        const temperatureObjects = cloneDevices
            .map(device => getTemperatureObject(device));

        let result = {};
        for (let i=0; i < temperatureObjects.length; i++) {
            const deviceName = temperatureObjects[i].deviceName;
            const newTemperature = generateTemperature(temperatureObjects[i].temperature);

            cloneDevices[i][deviceName] = newTemperature;
            result[deviceName] = newTemperature;
        }

        const bodyToSend = {
            "homeName": homeName,
            "deviceToTemperature": result
        };

        fetchDataPostWithBody("iot", bodyToSend);

        this.setState({
            devices: cloneDevices,
            isFetched: true
        });
    }

    render () {
        return (
            <div className="house">
                <span className="house-title">{this.state.name}</span>

                <div className="house-devices">
                    {this.state.devices.map(device => temperatureDevice(device, this.state.isFetched))}
                </div>
            </div>
        );
    }
}