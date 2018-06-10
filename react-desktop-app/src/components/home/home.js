require("./stylesheet.scss");

import React, {Component} from 'react';
import {Link} from 'react-router-dom';
import HeatingSystem from "../heating-system/heating-system";
import {fetchData} from "../../networking/networking";

export default class Home extends Component {

    constructor(props) {
        super(props);

        this.state = {
            homeName: props.match.params.homeName,
            homeExists: true,
            temperature: 0,
            temperatureDigit: 0
        }
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    componentWillMount() {
        fetchData(`api/home/${this.state.homeName}/exists`)
            .then(data => this.setState({homeExists: data}));
    }

    componentDidMount() {
        this.fetchTemperature();

        const intervalId = setInterval(() => this.fetchTemperature(), 1000 * 3);
        // store intervalId in the state so it can be accessed later:
        this.setState({
            intervalId: intervalId
        });
    }

    fetchTemperature() {
        if (this.state.homeExists) {
            fetchData(`api/home/${this.state.homeName}/temperature`)
                .then(data => data.temperatures)
                .then(temperatures => {
                    if (typeof temperatures !== "undefined" && temperatures !== null) {
                        const keys = Object.keys(temperatures);
                        const numberOfKeys = keys.length;

                        let temperature = 0;

                        keys.forEach(key => temperature += temperatures[key]);

                        temperature /= numberOfKeys;

                        const firstDigitAfterDot = ((temperature * 10) % 10).toFixed(0);
                        temperature = Math.floor(temperature);

                        this.setState({
                            temperature: temperature,
                            temperatureDigit: firstDigitAfterDot === "10" ? 0 : firstDigitAfterDot
                        });
                    }
                });
        }
    }

    render () {
        return (
            this.state.homeExists ?
                (<div className="house">
                    <div className="home-name">
                        {this.state.homeName}
                    </div>
                    <div className="current-temperature">
                        <span>{this.state.temperature}</span>
                        <span>.{this.state.temperatureDigit}</span>
                        <span>°C</span>
                    </div>
                    <HeatingSystem
                        homeName={this.state.homeName}
                    />
                </div>) :
                <div className="home-not-found">
                    <span>Home does not exist</span>
                    <Link
                        className="go-back-button"
                        to={"/"}>
                        Go back to houses list
                    </Link>
                </div>
        );
    };
}