package com.willmeyer.jrs232;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.comm.*;

import org.slf4j.*;

import com.willmeyer.util.*;

public abstract class Rs232Device {

	protected String portName = null;
	protected int portBaud = 9600;
	protected SerialPort port = null;
	protected CommDriver driver = null;
	protected boolean connected = false;
	protected boolean fakeIo = false;
	protected OutputStream outStream = null;
	protected InputStream inStream = null;
	protected STA sta = null;
	protected LinkedBlockingQueue<MarshalledCall> q = new LinkedBlockingQueue<MarshalledCall>();
	protected boolean staRunning = true;
	protected boolean useSta = true;
	
	protected final Logger logger = LoggerFactory.getLogger(Rs232Device.class);
	
	public void connectImpl() throws Exception {
		logger.debug("Thread {}: entered connect", Thread.currentThread().getName());
		if (connected) throw new IllegalStateException("Already connected!");
		if (!fakeIo) {
			this.openPortImpl(portName, portBaud);
		}
		outStream = this.getOutputStream();
		inStream = this.getInputStream();
		connected = true;
	}
	
	/**
	 * Connects to the device, using configured port settings.
	 */
	public synchronized void connect() throws Exception {
		if (useSta) {
			this.marshallCall("connect");
		} else {
			this.connectImpl();
		}
	}

	private void marshallCall(String name) {
		ArrayList<Object> params = new ArrayList<Object>();
		this.marshallCall(name, params);
	}

	private void marshallCall(String name, Object param) {
		ArrayList<Object> params = new ArrayList<Object>();
		params.add(param);
		this.marshallCall(name, params);
	}
	
	private void marshallCall(String name, List<Object> params) {
		MarshalledCall call = new MarshalledCall();
		call.methodName = name;
		call.params = params;
		try {
			
			synchronized (call) {
				
				// Stick it in the queue
				q.put(call);
			
				// Now wait on it
				call.wait();
			}
		} catch (Exception e) {
			logger.error("Error marshalling item to thread: {}", e);
		}
		
	}

	private InputStream getInputStream() throws IOException {
		if (fakeIo) {
			byte[] buf = new byte[10];
			return new ByteArrayInputStream(buf); // todo
		} else {
			return this.port.getInputStream();
		}
	}
	
	private OutputStream getOutputStream() throws IOException {
		if (fakeIo) {
			return new ByteArrayOutputStream(); 
		} else {
			return this.port.getOutputStream();
		}
	}

	public void disconnectImpl() {
		if (connected) {
			if (!fakeIo) {
				this.closePort();
			}
			outStream = null;
			inStream = null;
			connected = false;
		}
	}

	/**
	 * Disconnect, NOOP if not connected.
	 */
	public synchronized void disconnect() {
		if (useSta) {
			this.marshallCall("disconnect");
		} else {
			this.disconnectImpl();
		}
	}

	/**
	 * Does a port with this name exist?
	 */
	public boolean portExists(String portName) {
		try {
			CommPortIdentifier.getPortIdentifier(portName);
			return true;
		} catch (NoSuchPortException e) {
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void enumPorts() {
		logger.info("Enumerating serial ports...");
		Enumeration portList = CommPortIdentifier.getPortIdentifiers();
		while (portList.hasMoreElements()) {
			CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
			if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				logger.info("Port name: " + portId.getName());
			}
		}
		logger.info("Done");
	}

	protected class MarshalledCall {
		public String methodName;
		public List<Object> params;
	} 
	
	protected class STA extends Thread {
		
		public void run() {
			while (staRunning) {
				MarshalledCall call = null;
				try {
					call = q.poll(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
				}
				if (call != null) {
					synchronized (call) {
						logger.debug("Thread {}: executing marshalled call", Thread.currentThread().getName());
						try {
							if (call.methodName.equalsIgnoreCase("init")) {
								initSerialSubsystemImpl();
							} else if (call.methodName.equalsIgnoreCase("connect")) {
								connectImpl();
							} else if (call.methodName.equalsIgnoreCase("disconnect")) {
								disconnectImpl();
							} else if (call.methodName.equalsIgnoreCase("send")) {
								byte[] bytes = (byte[])call.params.get(0);
								sendBytesImpl(bytes);
							} else {
								throw new Exception ("invalid marshalled method, WTF?");
							}
						} catch (Exception e) {
							logger.error("Exception in STA execution: {}", e);
						}
						call.notify();
					}
				}
			}
		}
	}

	public synchronized void sendBytes(byte[] bytes) throws IOException {
		if (useSta) {
			this.marshallCall("send", bytes);
		} else {
			this.sendBytesImpl(bytes);
		}
	}
	
	public void sendBytesImpl(byte[] bytes) throws IOException {
		logger.debug("Thread {}: sending bytes", Thread.currentThread().getName());
		logger.debug("Sending: {}", Bytes.debugBytes(bytes));
		if (!fakeIo) {
			this.outStream.write(bytes);
			this.outStream.flush();
		}
		logger.debug("Bytes sent.");
	}
	
	public void sendByte(int theB) throws IOException {
		sendByte((byte)theB);
	}
	
	public synchronized void sendByte(byte theB) throws IOException {
		byte[] bytes = new byte[1];
		bytes[0] = theB;
		this.sendBytes(bytes);
	}

	/**
	 * Simple CTOR, 9600 baud, multi-threaded.
	 */
	public Rs232Device(String portName) throws Exception {
		this(portName, 9600, false);
	}
	
	/**
	 * Creates a device on a port at 8 data bits, 1 stop bit, no parity.  Initially unconnected.
	 *  
	 * @param portName COM1, etc., or MOCK to not actually open a real port
	 * @param portBaud The baud rate for the port
	 * @param useSta Whether or not to marshall all serial port management into one thread
	 */
	public Rs232Device(String portName, int portBaud, boolean useSta) throws Exception {
		this.portName = portName;
		this.portBaud = portBaud;
		this.useSta = useSta;
		logger.debug("Initializing port " + portName + " at " + portBaud + " baud, sta: " + useSta);
		if (portName.equalsIgnoreCase("MOCK")) 
			fakeIo = true;
		else {
			fakeIo = false;
			if (useSta) {
				sta = new STA();
				this.staRunning = true;
				sta.setDaemon(true);
				sta.start();
				this.marshallCall("init");
			} else {
				this.initSerialSubsystemImpl();
			}
		}
	}

	private void closePort() {
		if (fakeIo) return;
		try {
			port.close();
			port = null;
		} catch (Exception e) {
		}
	}

	private void initSerialSubsystemImpl() throws Exception {
		logger.debug("Thread {}: loading serial driver", Thread.currentThread().getName());
		String driverName = "com.sun.comm.Win32Driver";
		driver = (CommDriver)Class.forName(driverName).newInstance();
		driver.initialize();
	}

	private void openPortImpl(String portName, int baud) throws Exception {
		logger.debug("Thread {}: opening port", Thread.currentThread().getName());
		if (fakeIo) return;
		try {
			CommPortIdentifier bbPortId = CommPortIdentifier.getPortIdentifier(portName);
			port = (SerialPort) bbPortId.open("SimpleWrite", 2000);
			port.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			port.notifyOnOutputEmpty(true);
		} catch (NoSuchPortException e) {
			throw new Exception("Serial port \"" + portName + "\" is not available.");
		} catch (Exception e) {
			throw new Exception("Unable to open serial port: " + e.getMessage());
		}
	}

}
