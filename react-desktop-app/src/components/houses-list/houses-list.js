require("./stylesheet.scss");

import React, {Component} from 'react';
import {Link} from 'react-router-dom';

const createHouseEntry = (houseName, onClick) => (
    <div className="house-entry" onClick={() => onClick(houseName)}>
        {houseName}
    </div>
);

export default class HousesList extends Component {

    constructor(props) {
        super(props);

        this.state = {
            houses: props.houses,
            onHouseClick: props.onHouseClick
        };
    }

    componentWillReceiveProps(nextProps) {
        const nextHouses = nextProps.houses;

        this.setState({houses: nextHouses});
    }


    render () {
        return (
            <div className="houses-list">
                {this.state.houses ?
                    this.state.houses
                        .map(house => createHouseEntry(house, this.state.onHouseClick)) :
                    <span>No houses available</span>
                }
            </div>
        );
    }
}