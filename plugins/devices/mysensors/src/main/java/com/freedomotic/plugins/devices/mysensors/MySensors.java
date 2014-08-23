/**
 *
 * Copyright (c) 2009-2014 Freedomotic team http://freedomotic.com
 *
 * This file is part of Freedomotic
 *
 * This Program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 *
 * This Program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Freedomotic; see the file COPYING. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.freedomotic.plugins.devices.mysensors;

import com.freedomotic.api.EventTemplate;
import com.freedomotic.api.Protocol;
import com.freedomotic.exceptions.UnableToExecuteException;
import com.freedomotic.reactions.Command;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Mauro Cicolella <mcicolella@libero.it>
 */
public class MySensors extends Protocol {

    private static final Logger LOG = Logger.getLogger(MySensors.class.getName());
    private int GATEWAYS_NUMBER = 0;
    private final String DELIMITER = configuration.getStringProperty("delimiter", ",");
    ArrayList<MySensorsAbstractGateway> gateways = new ArrayList<MySensorsAbstractGateway>();

    public MySensors() {
        super("MySensors", "/mysensors/mysensors-manifest.xml");
    }

    @Override
    public boolean canExecute(Command c) {
        return false;
    }

    @Override
    public void onCommand(Command c) throws IOException, UnableToExecuteException {
        String housecode = c.getProperty("x10.address").substring(0, 1);
        String address = c.getProperty("x10.address").substring(1, 3);
        String command = c.getProperty("x10.function");
        MySensorsAbstractGateway dev = getGateway();
        //if we have to set brightness we turn of the light and increase brightness one step at time
        //this is done because X10 protocol its hard to synch as it haven't good status request features
        if (command.equalsIgnoreCase("BGT")) {
            int value = Integer.parseInt(c.getProperty("x10.brightness.value"));
            int loops = value / 5;
            System.out.println("set brightness " + value + " in " + loops + " steps");
            dev.send(dev.composeMessage(housecode, address, "OFF"));
            for (int i = 0; i < loops; i++) {
                dev.send(dev.composeMessage(housecode, address, "BGT"));
            }
        } else {
            dev.send(dev.composeMessage(housecode, address, command));
        }
    }

    private MySensorsAbstractGateway getGateway() {
        for (MySensorsAbstractGateway gateway : gateways) {
            if (gateway.getName().equalsIgnoreCase(configuration.getStringProperty("gateway.name", "PMIX35"))) {
                return gateway;
            }
        }
        return null;
    }

    @Override
    public void onStart() {
        GATEWAYS_NUMBER = configuration.getTuples().size();
        //create the gateways instances as defined in configuration using tuples
        loadGateways();
        //this.setPollingWait(3000);
        this.setPollingWait(-1);
    }

    @Override
    public void onStop() {
        for (MySensorsAbstractGateway gateway : gateways) {
            gateway.disconnect();
        }
        gateways.clear();
    }

    @Override
    protected void onRun() {
     //   try {
       //     for (MySensorsAbstractGateway gateway : gateways) {
       //         gateway.read();
       //     }
      //  } catch (IOException ex) {
      //      LOG.severe(ex.getMessage());
      //  }
    }

    @Override
    protected void onEvent(EventTemplate event) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void loadGateways() {
        for (int i = 0; i < GATEWAYS_NUMBER; i++) {
            // filter the tuples with "object.class" property
            String result = configuration.getTuples().getProperty(i, "object.class");
            // if the tuple doesn't have a "object.class" property it's a gateway configuration one
            if (result == null) {
                String gatewayName = configuration.getTuples().getStringProperty(i, "gateway.name", "");
                String gatewayPort = configuration.getTuples().getStringProperty(i, "gateway.port.name", "");
                Integer baudrate = configuration.getTuples().getIntProperty(i, "gateway.port.baudrate", 9600);


                Properties config = new Properties();
                config.setProperty("port", gatewayPort);
                config.setProperty("baudrate", String.valueOf(baudrate));

                MySensorsAbstractGateway gateway = new MySensorGateway(this, config);
                gateways.add(gateway);
            }
        }
    }
}
