/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip.server;

import com.sun.voip.BridgeVersion;
import com.sun.voip.CallEvent;
import com.sun.voip.FreeTTSClient;
import com.sun.voip.Logger;
import com.sun.voip.NetworkTester;

import java.io.File;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.jar.*;

import com.sun.stun.StunServerImpl;

/**
 * Conference Bridge
 *
 * Listen on a TCP socket for client requests.
 * Create a new RequestHandler to process the requests.
 *
 * For details on the command syntax see RequestParser.java
 */
public class Bridge extends Thread {

    private static String privateHost;
    private static int privateControlPort = 6666;
    private static int privateSipPort = 5060;

    private static String publicHost;
    private static int publicControlPort = 6666;
    private static int publicSipPort = 5060;

    private static String bridgeLocation;
    private static String logDirectory;
    private static char fileSep;

    private static boolean localhostSecurity = false;

    private static String defaultProtocol = "SIP";

    public static String getVersion() {
	return BridgeVersion.getVersion();
    }

    public static String getBridgeLocation() {
	return bridgeLocation;
    }

    public static void setBridgeLocation(String bridgeLocation) {
	Bridge.bridgeLocation = bridgeLocation;
    }

    public static String getBridgeLogDirectory() {
	return System.getProperty("user.dir") + fileSep + logDirectory;
    }

    public static String getPrivateHost() {
	return privateHost;
    }

    public static int getPrivateControlPort() {
	return privateControlPort;
    }

    public static int getPrivateSipPort() {
	return privateSipPort;
    }

    public static String getPublicHost() {
	return publicHost;
    }

    public static int getPublicControlPort() {
	return publicControlPort;
    }

    public static int getPublicSipPort() {
	return publicSipPort;
    }

    public static void setLocalhostSecurity(boolean localhostSecurity) {
	Bridge.localhostSecurity = localhostSecurity;
    }

    public static boolean getLocalhostSecurity() {
	return localhostSecurity;
    }

    public static void setDefaultProtocol(String defaultProtocol) {
	Bridge.defaultProtocol = defaultProtocol;
    }

    public static String getDefaultProtocol() {
	return defaultProtocol;
    }

    public static InetSocketAddress getLocalBridgeAddress() {
	return (InetSocketAddress) new InetSocketAddress(privateHost,
	    privateControlPort);
    }

    public static void main(String[] args) {
	String s = System.getProperty("file.separator");

	fileSep = s.charAt(0);

        new Bridge();
    }
        
    private Bridge() {
	bridgeLocation = System.getProperty(
	    "com.sun.voip.server.BRIDGE_LOCATION", "BUR");

	String security = System.getProperty(
            "com.sun.voip.server.LOCALHOST_SECURITY", "false");

	if (security.equalsIgnoreCase("true")) {
	    localhostSecurity = true;
	}

	Logger.init();

	Properties properties = new Properties();

	/*
	 * Create properties for the NIST SIP Stack
	 */
	properties.setProperty("javax.sip.STACK_NAME", "JAIN SIP 1.1");
	properties.setProperty("javax.sip.RETRANSMISSION_FILTER", "on");
	properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");

        logDirectory = System.getProperty(
            "com.sun.voip.server.Bridge.logDirectory", "." + fileSep + "log");

	File file = new File(logDirectory);

	if (file.exists() == false) {
	    if (file.mkdir() == false) {
		System.out.println("Unable to create directory " + logDirectory);
	    }
	} else  {
	    if (file.isDirectory() == false) {
		System.out.println(logDirectory + " is not a directory");
	    }
	}

	String serverLog = System.getProperty(
	    "gov.nist.javax.sip.SERVER_LOG", "sipServer.log");

        if (serverLog.charAt(0) != fileSep) {
            serverLog = logDirectory + fileSep + serverLog;

	    properties.setProperty(
	        "gov.nist.javax.sip.SERVER_LOG", serverLog);
        }

	String debugLog = System.getProperty(
	    "gov.nist.javax.sip.DEBUG_LOG", "nistStack.log");

        if (debugLog.charAt(0) != fileSep) {
            debugLog = logDirectory + fileSep + debugLog;

	    properties.setProperty(
	        "gov.nist.javax.sip.DEBUG_LOG", debugLog);
        }
	
	Logger.println("Built on " + BuildDate.getBuildDate());

        Logger.println("Running java version "
	    + System.getProperty("java.version"));

	Logger.println("OS Name = " + System.getProperty("os.name")
	    + ", OS Arch = " + System.getProperty("os.arch")
	    + ", OS Version = " + System.getProperty("os.version"));

	Logger.println("user.dir = " + System.getProperty("user.dir"));

	try {
	    initAddresses();
	} catch (IOException e) {
	    Logger.error(e.getMessage());
            System.exit(1);
        }

        Logger.println("Bridge started in location '"
            + getBridgeLocation() + "'");

	Logger.println("Bridge server private control port:  " + privateControlPort);

	new SipServer(privateHost, properties);   // Initialize SIP stack.

	publicSipPort = SipServer.getSipAddress().getPort();

	try {
	    new StunServerImpl().startServer();
	} catch (IOException e) {
	    Logger.println("Unable to start STUN Server:  " + e.getMessage());
	}

        try {
            Logger.println("Initializing FreeTTSClient...");
            FreeTTSClient.initialize();
            Logger.println("FreeTTSClient Initialization done...");
        } catch (Throwable e) {
            Logger.println("Can't start FreeTTSClient (ignoring) " 
		+ e.getMessage());
        }

        try {
            new NetworkTester();
        } catch (IOException e) {
            Logger.println("Failed to start NetworkTester:  " 
                + e.getMessage());
        }

        String modPath = System.getProperty("com.sun.voip.server.MODULES_PATH");

        if (modPath == null || modPath.length() == 0) {
            modPath = "modules";
        }

        if (modPath.charAt(0) != fileSep) {
            modPath = System.getProperty("user.dir") + fileSep + modPath;
        }

	modPath += fileSep;

	try {
	    new ModuleLoader(modPath);
	} catch (IOException e) {
	    Logger.println("Optional modules failed to load:  " 
		+ e.getMessage());
	}

	try {
	    new ReceiveMonitor();
	} catch (IOException e) {
	    Logger.println("Unable to start ReceiveMonitor:  " 
		+ e.getMessage());
	}

        String longDistancePrefix = System.getProperty(
           "com.sun.voip.server.LONG_DISTANCE_PREFIX");

        if (longDistancePrefix != null) {
            RequestHandler.setLongDistancePrefix(longDistancePrefix);
            Logger.println("Outside line prefix set to '" + longDistancePrefix + "'");
        } 

        String outsideLinePrefix = System.getProperty(
           "com.sun.voip.server.OUTSIDE_LINE_PREFIX");

        if (outsideLinePrefix != null) {
            RequestHandler.setOutsideLinePrefix(outsideLinePrefix);
            Logger.println("Outside line prefix set to '" + outsideLinePrefix + "'");
        } 

        String internationalPrefix = System.getProperty(
           "com.sun.voip.server.INTERNATIONAL_PREFIX");

        if (internationalPrefix != null) {
            RequestHandler.setInternationalPrefix(internationalPrefix);
            Logger.println("International prefix set to '" + internationalPrefix 
		+ "'");
        } 

	Logger.println("");
	Logger.println("The Bridge is initialized and Ready");
	Logger.println("");

        start();
    }
    
    public static void initAddresses() throws IOException {
        try {
            String localHostAddress =
                    System.getProperty("javax.sip.IP_ADDRESS");
	    
            if (localHostAddress == null || localHostAddress.length() == 0) {
                localHostAddress = InetAddress.getLocalHost().getHostAddress();
                
                Logger.println(
                        "Using local host from InetAddress.getLocalHost(): "
                        + localHostAddress);
            } else {
                Logger.println(
                        "Using localhost System property javax.sip.IP_ADDRESS: "
                        + localHostAddress);

		if (localHostAddress.equalsIgnoreCase("localhost")) {
                    localHostAddress = InetAddress.getLocalHost().getHostAddress();
		}
            }
            
	    if (localHostAddress.startsWith("127.")) {
		Logger.println("WARNING:  THE BRIDGE'S IP ADDRESS IS " 
		    + localHostAddress + ".  THE BRIDGE WILL ONLY WORK "
		    + "WITH CLIENTS ON THIS MACHINE!");
	    }

	    /*
	     * For some reason on Linux, the local hostname is set to 127.0.1.1
	     * in /etc/hosts.
	     */
	    if (localHostAddress.startsWith("127.0.1.1")) {
		String hostname = InetAddress.getLocalHost().getHostName();

		Logger.println("\n------------WARNING-------------\n"
		    + "   The voicebridge local address is 127.0.1.1.\n"
		    + "   Notice this is not the same as   127.0.0.1 \n"
		    + "   This will only work if all clients use 127.0.1.1.\n"
		    + "   If audio does not work, edit /etc/hosts and change the entry\n"
		    + "   for '" + hostname + "' to the real IP address.\n"
		    + "------------WARNING-------------\n");
	    }

            privateHost = InetAddress.getByName(localHostAddress).getHostAddress();
        } catch (UnknownHostException e) {
	    Logger.error("Unable to determine local IP Address:  "
		+ e.getMessage());
  	    Logger.println("You will only be able to place calls locally.");
	    Logger.println("If you need to run with other hosts, "
		+ "you must restart and specify "
		+ "-Djavax.sip.IP_ADDRESS=<ip address>");
        }

	if (privateHost == null) {
	    privateHost = InetAddress.getLocalHost().getHostAddress();

	    Logger.println("Defaulting to localHost:  " + privateHost);
	}

	System.setProperty("com.sun.voip.server.BRIDGE_SERVER_ADDRESS", privateHost);

	String s = System.getProperty(
	    "com.sun.voip.server.BRIDGE_CONTROL_PORT", 
	    String.valueOf(privateControlPort));

	try {
	    privateControlPort = Integer.parseInt(s);
	} catch (NumberFormatException e) {
            Logger.error("NumberFormatException for " 
		+ "com.sun.voip.server.Bridge.controlPort, " 
		+ "using default value " + privateControlPort);
        }

	System.setProperty("com.sun.voip.server.BRIDGE_CONTROL_PORT",
	    String.valueOf(privateControlPort));

	try {
	    s = System.getProperty("gov.nist.jainsip.stack.enableUDP");
	    privateSipPort = Integer.parseInt(s);
        } catch (NumberFormatException e) {
	    Logger.println("Invalid private sip port:  " + s + " " 
		+ e.getMessage());
        }

	publicHost = System.getProperty("com.sun.voip.server.PUBLIC_IP_ADDRESS",
	    privateHost);

	s = System.getProperty(
	    "com.sun.voip.server.BRIDGE_PUBLIC_CONTROL_PORT", 
	    String.valueOf(privateControlPort));

	publicControlPort = privateControlPort;

	try {
	    publicControlPort = Integer.parseInt(s);
	} catch (NumberFormatException e) {
            Logger.error("NumberFormatException for public control port. " 
		+ "Using default value " + publicControlPort);
        }
    }

    /**
     * create a socket, listen for connections, start dialing...
     * @param properties Properties used by the SIP Stack
     */ 
    public void run() {
	try {
	    ServerSocket serverSocket = new ServerSocket(privateControlPort);

	    String s = System.getProperty("com.sun.voip.server.BRIDGE_STATUS_LISTENERS");

	    if (s == null || s.length() == 0) {
                Logger.println("There are no listeners to notify "
		    + "that this bridge came online");
            } else {
	        String[] listeners = s.split(",");

	        for (int i = 0; i < listeners.length; i++) {
		    new BridgeStatusNotifier(listeners[i]);
	        }
	    }

	    while (true) {
		Socket socket = serverSocket.accept(); // wait for a connection

		InetAddress ia = socket.getInetAddress();

		String host;

		try {
		    host = ia.getHostName();
		} catch (Exception e) {
		    host = ia.toString();
		}

		if (localhostSecurity == true) {
		    if (ia.isSiteLocalAddress() == false &&
			    host.equalsIgnoreCase("localhost") == false &&
			    host.startsWith("127.") == false &&
			    ia.getHostAddress().equals(privateHost) == false) {

			s = "Connection from " + ia 
			    + " rejected:  must connect from "
			    + "site local address\n";

			Logger.error(s);

			DataOutputStream output = new DataOutputStream(
			    socket.getOutputStream());

			try {
			    output.write(s.getBytes());
			} catch (IOException e) {
			}

			socket.close();
			continue;

		    }
		}
		
		Logger.println("New connection accepted from " 
		    + host + ":" + socket.getPort());

	        /*
		 * Start a new request handler Thread and listen 
		 * for new connections.
	         */
		try {
		    RequestHandler requestHandler = new RequestHandler(socket);
		} catch (IOException e) {
		    Logger.error("IOException while starting request for "
			+ socket.getInetAddress() + ":" + socket.getPort());
		}
	    }
	} catch (IOException e) {
	    Logger.error("can't create server socket with port " 
		+ privateControlPort + " " + e.getMessage());
	    System.exit(-1);
	}
    }

    /*
     * XXX For debugging.  Send a packet to the VoIP gateway DISCARD port.
     */
    private static DatagramSocket datagramSocket;

    public static void sendMarkerPacket(String s) {
        InetAddress ia;

        DatagramPacket datagramPacket;

        try {
	    if (datagramSocket == null) {
                datagramSocket = new DatagramSocket();
	    }

	    InetAddress gateway = InetAddress.getByName(
		GatewayManager.getVoIPGateways().get(0).getAddress().getHostAddress());

            datagramPacket = new DatagramPacket(
		s.getBytes(), s.length(), gateway, 9);

            datagramSocket.send(datagramPacket);
	} catch (InterruptedIOException e) {
	    // happens at the end of a conference.  Just ignore it.
	    if (datagramSocket != null) {
		datagramSocket.close();
		datagramSocket = null;
	    }
        } catch (Exception e) {
            Logger.error("can't send marker packet " + e.getMessage());
	    e.printStackTrace();
	    if (datagramSocket != null) {
		datagramSocket.close();
		datagramSocket = null;
	    }
        }
    }

    private static final int MAX_SECONDS_TO_SEND = 300;

    public static void testUdp(RequestHandler requestHandler, InetAddress dest, 
	    int port, int seconds) {

	new UdpTester(requestHandler, dest, port, seconds);
    }

    static class UdpTester extends Thread {

	private RequestHandler requestHandler;
	private InetAddress dest;
	private int port;
	private int seconds;

	public UdpTester(RequestHandler requestHandler, InetAddress dest, int port, int seconds) {
	    this.requestHandler = requestHandler;
	    this.dest = dest;
	    this.port = port;
	    this.seconds = seconds;

	    start();
	}

	public void run() {
	    if (seconds > MAX_SECONDS_TO_SEND) {
	        Logger.println("Setting time to test udp to Max " + MAX_SECONDS_TO_SEND);
	        seconds = MAX_SECONDS_TO_SEND;
	    }

	    InetSocketAddress isa = new InetSocketAddress(getPrivateHost(), 0);

	    DatagramSocket datagramSocket;

	    try {
	        datagramSocket = new DatagramSocket(isa);
		datagramSocket.setSoTimeout(3000);
	    } catch (SocketException e) {
		println("Unable to create Datagram Socket " + isa + " " + e.getMessage());
		return;
	    }

	    byte[] buf = new byte[1260];

	    int packetNumber = 0;

	    long endTime = System.currentTimeMillis() + seconds * 1000;

	    while (System.currentTimeMillis() < endTime) {
                println("Sending packet " + packetNumber + ", length "
                    + buf.length + " bytes " + "to " + port + ".  ");

                buf[0] = (byte) (packetNumber >> 24);
                buf[1] = (byte) (packetNumber >> 16);
                buf[2] = (byte) (packetNumber >> 8);
                buf[3] = (byte) (packetNumber & 0xff);

                DatagramPacket p = new DatagramPacket(buf, buf.length, dest, port);

                try {
                    datagramSocket.send(p);
		} catch (IOException e) {
		    println("Unable to send to " + dest + " " + port);
		    return;
		}

		try {
		    datagramSocket.receive(p);
		    packetNumber++;
		} catch (SocketTimeoutException e) {
		    println("Receive timed out on port " + port + "!");
		} catch (IOException e) {
		    println("received failed on port " + port + " " + e.toString());
		    return;
		}

	        try {
		    Thread.sleep(1000);
	        } catch (InterruptedException e) {
		}
	    }

	    println("Done testing UDP port " + port);
	}

	private void println(String message) {
	    Logger.println(message);

	    CallEvent callEvent = new CallEvent(CallEvent.TEST_UDP_PORT);

	    callEvent.setInfo(message);
	    requestHandler.writeToSocket(callEvent.toString());
	}

    }

}
