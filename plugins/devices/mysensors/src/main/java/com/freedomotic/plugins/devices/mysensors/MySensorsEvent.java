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

import com.freedomotic.app.Freedomotic;
import com.freedomotic.events.ProtocolRead;

/**
 * An implementation of a generic MySensor event
 *
 * @author Mauro Cicolella <mcicolella@libero.it>
 */

public class MySensorsEvent {

    private String objectName;
    private String objectAddress;
    private String objectClass;
    private String sensorType;
    private String value;

    public MySensorsEvent() {
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String name) {
        this.objectName = name;
    }

    public String getObjectAddress() {
        return objectAddress;
    }

    public void setObjectAddress(String address) {
        this.objectAddress = address;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(String objectClass) {
        this.objectClass = objectClass;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void send() {
        ProtocolRead event = new ProtocolRead(this, "mysensors", getObjectAddress());

        event.addProperty("object.name", getObjectName());
        event.addProperty("object.class", getObjectClass());
        event.addProperty("sensor.value", getValue());

        Freedomotic.sendEvent(event);
    }
}
