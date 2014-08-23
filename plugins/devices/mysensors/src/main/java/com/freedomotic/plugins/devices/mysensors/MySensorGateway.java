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

import com.freedomotic.serial.SerialConnectionProvider;
import com.freedomotic.serial.SerialDataConsumer;
import java.io.IOException;
import java.util.Properties;

/**
 * An implementation of a generic MySensor gateway
 *
 * @author Mauro Cicolella <mcicolella@libero.it>
 */
public final class MySensorGateway implements MySensorsAbstractGateway, SerialDataConsumer {

    private static SerialConnectionProvider usb = null;
    private String lastReceived = "";
    private MySensorsEvent mySensorsEvent = new MySensorsEvent();
    private MySensors plugin;
    private Properties config;

    public MySensorGateway(MySensors plugin, Properties config) {
        this.plugin = plugin;
        this.config = config;
        connect();
    }

    /**
     * Connection with the gateway over serial port
     */
    @Override
    public void connect() {
        if (usb == null) {
            usb = new SerialConnectionProvider(config); //instantiating a new serial connection with the previous parameters
            usb.addListener(this);
            usb.connect();
            if (usb.isConnected()) {
                plugin.setDescription("Connected to " + usb.getPortName());
            } else {
                plugin.setDescription("Unable to connect to " + config.getProperty("port"));
                plugin.stop();
            }
        }
    }

    @Override
    public void disconnect() {
        if (usb != null) {
            usb.disconnect();
            if (!usb.isConnected()) {
                plugin.setDescription("Disconnected");
            }
        }
    }

    @Override
    public String send(String message) throws IOException {
        String reply = usb.send(message);
        return reply;
    }

    /**
     * Parse the gateway readed line to find extract data
     *
     * @param readed
     * @return
     */
    @Override
    public void parseReaded(String readed) {

        String[] splittedReaded = null;
        Integer radioID = 0;
        Integer childID = 0;
        Integer messageType = 0;
        Integer subType = 0;
        Integer payload = 0;

        //if nothing is readed the gateway is no longer connected to usb
        //if (readed.isEmpty()) {
        //  usb.disconnect();
        // }

        //removing the '\n' at the end of the string
        readed = readed.substring(0, readed.length() - 1);

        splittedReaded = readed.split(",");
        radioID = Integer.valueOf(splittedReaded[0]);
        childID = Integer.valueOf(splittedReaded[1]);
        messageType = Integer.valueOf(splittedReaded[2]);
        subType = Integer.valueOf(splittedReaded[3]);
        payload = Integer.valueOf(splittedReaded[4]);

        switch (messageType) {
            // sent by sensors when they present which sensors they have attached.
            // This is usually done when they start up.
            case 0:

            // This message is sent from or to a sensor when a sensor value should be updated
            case 1:
                switch (subType) {
                    case 0:
                        mySensorsEvent.setObjectClass("Thermostat");



                    case 1:
                        mySensorsEvent.setObjectClass("Hygrometer");


                }

            // Requests a variable value (usually from an actuator to gateway).    
            case 2:

            // When controller/gateway sends out a updated variable value (e.g. relay) 
            // the actuator replies with one of these messages.    
            case 3:

            // This is a special internal message. See table below for the details
            case 4:


        }


        mySensorsEvent.setObjectAddress(radioID + ":" + childID);
        mySensorsEvent.setValue(String.valueOf(payload));
        //mySensorsEvent.send();

    }

    /**
     *
     * @param the string to translate in PMIX35 format
     * @return the encoded string ready to be sent to the PMIX35
     */
    @Override
    public String composeMessage(String housecode, String address, String command) {
        //Adding the standard prefix
        String message =
                "$>9000LW "
                + housecode + address
                + housecode + address
                + " "
                + housecode + command
                + housecode + command;

        //Calculate the chacksum
        int sum = 0;
        for (int i = 0; i < message.length(); i++) {
            sum += message.charAt(i);
        }
        //we need only the last to char of the checksum
        String cs = Integer.toHexString(sum);
        cs = cs.substring(cs.length() - 2);
        cs = cs.toUpperCase();

        //Adding the end of line character and construction the whole encoded string
        return message + cs + "#\r\n";
    }

    @Override
    public String getName() {
        return "PMIX35";
    }

    @Override
    public void onDataAvailable(String readed) {

        System.out.println("Stringa letta " + readed);

    }

    
}
