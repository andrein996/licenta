require("./stylesheet.scss");

import React, {Component} from 'react';
import {Switch, Route} from 'react-router-dom';
import Home from './components/home/home';
import Houses from './components/houses/houses';

export default class App extends Component {

    constructor(props) {
        super(props);
    }

    render () {
        return (
            <div className="wrapper">
                <Switch>
                    <Route exact path='/' component={Houses}/>
                    <Route path='/home/:homeName' component={Home}/>
                </Switch>
            </div>
        );
    }
}