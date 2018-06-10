require("./stylesheet.scss");

import React, {Component} from 'react';
import {fetchData} from "./networking/networking";
import HousesGrid from "./components/houses-grid/houses-grid"

export default class App extends Component {

    constructor(props) {
        super(props);

        this.state = {
            houses: null,
            intervalId: null
        };

        this.refresh = this.refresh.bind(this);
    }

    componentWillMount() {
        fetchData("iot")
            .then(data => this.setState({houses: data}));
    }

    componentDidMount() {
        const intervalId = setInterval(() => this.timer(), 1000 * 3);
        // store intervalId in the state so it can be accessed later:
        this.setState({intervalId: intervalId});
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    timer() {
        fetchData("iot")
            .then(data => this.setState({houses: data}));

        if (this.state.houses !== null) {
            clearInterval(this.state.intervalId);
        }
    }

    refresh() {
        console.log("Refreshing");
        fetchData("iot")
            .then(data => this.setState({houses: data}));
    }

    render () {
        return (
            <div className="main">
                {this.state.houses ?
                    (
                        <div>
                            <div className="main-refresh-button" onClick={this.refresh}>Refresh</div>
                            <HousesGrid
                                houses={this.state.houses}
                            />
                        </div>
                    ) :
                    <p>No houses available at the moment</p>}
            </div>
        );
    }
}