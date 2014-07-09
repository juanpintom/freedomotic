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
package com.freedomotic.plugins.devices.wifipower;

import com.freedomotic.api.EventTemplate;
import com.freedomotic.api.Protocol;
import com.freedomotic.app.Freedomotic;
import com.freedomotic.events.ProtocolRead;
import com.freedomotic.exceptions.UnableToExecuteException;
import com.freedomotic.reactions.Command;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class WifiPower extends Protocol {

    private static final Logger LOG = Logger.getLogger(WifiPower.class.getName());
    private static ArrayList<Board> boards = null;
    Map<String, Board> devices = new HashMap<String, Board>();
    private static int BOARD_NUMBER = 1;
    private static int POLLING_TIME = 1000;
    private Socket socket = null;
    private DataOutputStream outputStream = null;
    private BufferedReader inputStream = null;
    private String[] address = null;
    private int SOCKET_TIMEOUT = configuration.getIntProperty("socket-timeout", 1000);
    private String GET_STATUS_URL = configuration.getStringProperty("get-status-url", "/Q");

    /**
     * Initializations
     */
    public WifiPower() {
        super("WifiPower", "/wifipower/wifipower-manifest.xml");
        setPollingWait(POLLING_TIME);
    }

    private void loadBoards() {
        if (boards == null) {
            boards = new ArrayList<Board>();
        }
        if (devices == null) {
            devices = new HashMap<String, Board>();
        }
        setDescription("Reading status changes from"); //empty description
        for (int i = 0; i < BOARD_NUMBER; i++) {
            String ipToQuery;
            String relayTag;
            String autoConfiguration;
            String objectClass;
            String alias;
            String monitorRelay;
            String authentication;
            String username;
            String password;
            int portToQuery;
            int relayNumber;
            int startingRelay;
            ipToQuery = configuration.getTuples().getStringProperty(i, "ip-to-query", "192.168.1.201");
            portToQuery = configuration.getTuples().getIntProperty(i, "port-to-query", 80);
            alias = configuration.getTuples().getStringProperty(i, "alias", "default");
            relayNumber = configuration.getTuples().getIntProperty(i, "relay-number", 4);
            startingRelay = configuration.getTuples().getIntProperty(i, "starting-relay", 0);
            authentication = configuration.getTuples().getStringProperty(i, "authentication", "false");
            username = configuration.getTuples().getStringProperty(i, "username", "ftp");
            password = configuration.getTuples().getStringProperty(i, "password", "2406");
            relayTag = configuration.getTuples().getStringProperty(i, "relay-tag", "out");
            autoConfiguration = configuration.getTuples().getStringProperty(i, "auto-configuration", "false");
            monitorRelay = configuration.getTuples().getStringProperty(i, "monitor-relay", "true");
            objectClass = configuration.getTuples().getStringProperty(i, "object.class", "Light");
            Board board = new Board(ipToQuery, portToQuery, alias, relayNumber, startingRelay, relayTag, autoConfiguration, objectClass,
                    monitorRelay, authentication, username, password);
            boards.add(board);
            // add board object and its alias as key for the hashmap
            devices.put(alias, board);
            setDescription(getDescription() + " " + ipToQuery + ":" + portToQuery + ";");
        }
    }

    /**
     * Connection to boards
     */
    private boolean connect(String address, int port) {

        LOG.info("Trying to connect to WifiPower board on address " + address + ':' + port);
        try {
            //TimedSocket is a non-blocking socket with timeout on exception
            socket = TimedSocket.getSocket(address, port, SOCKET_TIMEOUT);
            socket.setSoTimeout(SOCKET_TIMEOUT); //SOCKET_TIMEOUT ms of waiting on socket read/write
            BufferedOutputStream buffOut = new BufferedOutputStream(socket.getOutputStream());
            outputStream = new DataOutputStream(buffOut);
            return true;
        } catch (IOException e) {
            LOG.severe("Unable to connect to host " + address + " on port " + port);
            return false;
        }
    }

    private void disconnect() {
        // close streams and socket
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (Exception ex) {
            //do nothing. Best effort
        }
    }

    /**
     * Sensor side
     */
    @Override
    public void onStart() {
        super.onStart();
        POLLING_TIME = configuration.getIntProperty("polling-time", 1000);
        BOARD_NUMBER = configuration.getTuples().size();
        setPollingWait(POLLING_TIME);
        loadBoards();
    }

    @Override
    public void onStop() {
        super.onStop();
        //release resources
        boards.clear();
        boards = null;
        devices.clear();
        devices = null;
        setPollingWait(-1); //disable polling
        //display the default description
        setDescription(configuration.getStringProperty("description", "WifiPower"));
    }

    @Override
    protected void onRun() {
        // select all boards in the devices hashmap and evaluate the status
        Set<String> keySet = devices.keySet();
        for (String key : keySet) {
            Board board = devices.get(key);
            try {
                evaluateDiffs(getXMLStatusFile(board), board);
            } catch (XPathExpressionException ex) {
                Logger.getLogger(WifiPower.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            Thread.sleep(POLLING_TIME);
        } catch (InterruptedException ex) {
            Logger.getLogger(WifiPower.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Document getXMLStatusFile(Board board) {
        final Board b = board;
        //get the xml file from the socket connection
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(WifiPower.class.getName()).log(Level.SEVERE, null, ex);
        }
        Document doc = null;
        String statusFileURL = null;
        try {
            if (board.getAuthentication().equalsIgnoreCase("true")) {
                Authenticator.setDefault(new Authenticator() {

                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(b.getUsername(), b.getPassword().toCharArray());
                    }
                });
                statusFileURL = "http://" + b.getUsername() + ":" + b.getPassword() + "@" + b.getIpAddress() + ":"
                        + Integer.toString(b.getPort()) + GET_STATUS_URL;
            } else {
                statusFileURL = "http://" + b.getIpAddress() + ":"
                        + Integer.toString(b.getPort()) + GET_STATUS_URL;
            }

            LOG.info("WifiPower gets relay status from file " + statusFileURL);
            doc = dBuilder.parse(new URL(statusFileURL).openStream());
            doc.getDocumentElement().normalize();
        } catch (ConnectException connEx) {
            disconnect();
            this.stop();
            this.setDescription("Connection timed out, no reply from the board at " + statusFileURL);
        } catch (SAXException ex) {
            disconnect();
            this.stop();
            LOG.severe(Freedomotic.getStackTraceInfo(ex));
        } catch (Exception ex) {
            disconnect();
            this.stop();
            setDescription("Unable to connect to " + statusFileURL);
            LOG.severe(Freedomotic.getStackTraceInfo(ex));
        }
        return doc;

    }

    private void evaluateDiffs(Document doc, Board board) throws XPathExpressionException {
        //parses xml
        if (doc != null && board != null) {
            //Node n = doc.getFirstChild();
            if (board.getMonitorRelay().equalsIgnoreCase("true")) {
                // valueTag(doc, board, board.getRelayNumber(), board.getRelayTag());
                valueTag(doc, board, board.getRelayNumber(), "out");
            }
        }
    }

    private void valueTag(Document doc, Board board, Integer nl, String tag) throws XPathExpressionException {
        //  try {
        //creating an XPathFactory:
        XPathFactory factory = XPathFactory.newInstance();
        //using this factory to create an XPath object: 
        XPath xpath = factory.newXPath();

        // XPath Query for showing all nodes value
        XPathExpression expr = xpath.compile("//" + "out" + "/*");
        Object result = expr.evaluate(doc, XPathConstants.NODESET);
        NodeList nodes = (NodeList) result;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node el = (Node) nodes.item(i);
            System.out.println("tag: " + el.getNodeName() + " value: " + el.getTextContent());
            //if (!(board.getRelayStatus(j) == Integer.parseInt(children.item(j).getNodeValue()))) {
            sendChanges(board, el.getTextContent(), el.getNodeName());
            board.setRelayStatus(i, Integer.parseInt(el.getTextContent()));

        }
    }

    private void sendChanges(Board board, String status, String tag) {

        //reconstruct freedomotic object address
        String address = board.getAlias() + ":" + tag;
        LOG.info("Sending WifiPower protocol read event for object address '" + address + "'. It's readed status is " + status);
        //building the event
        ProtocolRead event = new ProtocolRead(this, "wifipower", address); //IP:PORT:RELAYLINE
        // relay lines - status=0 -> off; status=1 -> on
        if (status.equals("0")) {
            event.addProperty("isOn", "false");
        } else {
            event.addProperty("isOn", "true");
            //if autoconfiguration is true create an object if not already exists
            if (board.getAutoConfiguration().equalsIgnoreCase("true")) {
                event.addProperty("object.class", board.getObjectClass());
                event.addProperty("object.name", address);
            }
        }
        //publish the event on the messaging bus
        this.notifyEvent(event);
    }

    /**
     * Actuator side
     */
    @Override
    public void onCommand(Command c) throws UnableToExecuteException {
        String delimiter = configuration.getProperty("address-delimiter");
        address = c.getProperty("address").split(delimiter);
        Board board = (Board) devices.get(address[0]);
        if (c.getProperty("command").equals("CHANGE-STATE-RELAY")) {
            changeRelayStatus(board, c);
        }
    }

    private void changeRelayStatus(Board board, Command c) {
        try {
            URL url = null;
            URLConnection urlConnection;
            String delimiter = configuration.getProperty("address-delimiter");
            String[] address = c.getProperty("address").split(delimiter);
            String relayNumber = address[1];

            // if required set the authentication
            if (board.getAuthentication().equalsIgnoreCase("true")) {
                String authString = board.getUsername() + ":" + board.getPassword();
                byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
                String authStringEnc = new String(authEncBytes);
                //Create a URL for the desired  page   
                url = new URL("http://" + board.getUsername() + ":" + board.getPassword() + "@" + board.getIpAddress() + ":" + board.getPort() + "/R" + relayNumber + c.getProperty("behavior"));
                urlConnection = url.openConnection();
                urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
            } else {
                //Create a URL for the desired  page   
                url = new URL("http://" + board.getIpAddress() + ":" + board.getPort() + "/R" + relayNumber + c.getProperty("behavior"));
                urlConnection = url.openConnection();
            }
            LOG.info("Freedomotic sends the command " + url);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            int numCharsRead;
            char[] charArray = new char[1024];
            StringBuffer sb = new StringBuffer();
            while ((numCharsRead = isr.read(charArray)) > 0) {
                sb.append(charArray, 0, numCharsRead);
            }
            String result = sb.toString();
        } catch (MalformedURLException e) {
            LOG.severe("Change relay status malformed URL " + e.toString());
        } catch (IOException e) {
            LOG.severe("Change relay status IOexception" + e.toString());
        }
    }

    @Override
    protected boolean canExecute(Command c) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void onEvent(EventTemplate event) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    // retrieve a key from value in the hashmap 
    public static Object getKeyFromValue(Map hm, Object value) {
        for (Object o : hm.keySet()) {
            if (hm.get(o).equals(value)) {
                return o;
            }
        }
        return null;
    }
}
