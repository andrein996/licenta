require("./stylesheet.scss");

import React, {Component} from 'react';
import {Link} from 'react-router-dom';
import HousesList from "../houses-list/houses-list";
import {fetchData} from "./../../networking/networking"

export default class Houses extends Component {

    constructor(props) {
        super(props);

        this.state = {
            inputValue: null,
            houses: null
        };

        this.textInput = React.createRef();
        this.onClick = this.onClick.bind(this);
        this.onClickHouseEntry = this.onClickHouseEntry.bind(this);
        this.updateInputValue = this.updateInputValue.bind(this);
    }

    componentWillMount() {
        this.housesFetch();
    }

    componentDidMount() {
        const intervalId = setInterval(() => this.housesFetch(), 1000 * 15);
        // store intervalId in the state so it can be accessed later:
        this.setState({
            intervalId: intervalId
        });
    }

    componentWillUnmount() {
        // use intervalId from the state to clear the interval
        clearInterval(this.state.intervalId);
    }

    housesFetch() {
        fetchData("iot")
            .then(data => {
                if (data !== null) {
                    this.setState({houses: Object.keys(data)})
                }
            });
    }

    onClickHouseEntry(houseName) {
        this.setState({inputValue: houseName})
    }

    onClick() {
        console.log(this.state.inputValue);
    }

    updateInputValue(evt) {
        this.setState({
            inputValue: evt.target.value
        });
    }

    render () {
        return (
            <div className="main">
                <div className="main-left">
                    <HousesList
                        onHouseClick={this.onClickHouseEntry}
                        houses={this.state.houses}
                    />
                </div>
                <div className="main-right">
                    <input
                        className="home-input"
                        spellCheck={false}
                        ref={this.textInput}
                        onChange={this.updateInputValue}
                        value={this.state.inputValue}/>
                    <Link
                        className="go-button"
                        to={`home/${this.state.inputValue}`}>GO</Link>
                </div>
            </div>
        );
    }
}