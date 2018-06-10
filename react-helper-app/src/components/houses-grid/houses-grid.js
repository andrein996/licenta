require("./stylesheet.scss");

import React, {Component} from 'react';
import House from './../house/house';
import {fetchDataPut} from "../../networking/networking";

const createHouseObject = (key, devices) => (
    {
        name: key,
        devices: devices
    }
);

const timer = () => fetchDataPut("iot/heating");

export default class HousesGrid extends Component {

    constructor(props) {
        super(props);

        this.state = {
            houses: props.houses
        };
    }

    componentDidMount() {
        const intervalId = setInterval(() => timer(), 1000 * 30);
        // store intervalId in the state so it can be accessed later:
        this.setState({intervalId: intervalId});
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    componentWillReceiveProps(nextProps) {
        const nextHouses = nextProps.houses;

        this.setState({
            houses: nextHouses
        });
    }

    render () {
        const keys = Object.keys(this.state.houses);

        return (
            <div className="wrapper">
                <h2>These are the houses: </h2>

                <div className="houses">
                    {keys
                        .map(key => createHouseObject(key, this.state.houses[key]))
                        .map((house, index) => (
                            <House
                                key={index}
                                name={house.name}
                                devices={house.devices}
                            />
                        ))}
                </div>
            </div>
        );
    }
}