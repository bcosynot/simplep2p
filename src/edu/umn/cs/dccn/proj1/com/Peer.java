package edu.umn.cs.dccn.proj1.com;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umn.cs.dccn.proj1.util.LoggerHelper;
import edu.umn.cs.dccn.proj1.util.MessageCommands;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * <p>Creates a Peer host. The basic functionalities are:</p>
 * <p> - Registers itself on the specified central server</p>
 * <p> - Can quit from the central server</p>
 * <p> - Search and download files from the P2P network</p>
 * <p> - Send files to another peer</p>
 * 
 * @author Vivek Ranjan
 * 
 */
public class Peer {

	// Main properties of the Peer
	/** List of all the neighbour's */
	private static List<String> neighbours = new ArrayList<String>();;
	/** Central server's address */
	private static String centralServer = "";
	/** Full path of the working directory */
	private static String workingDirectory = "";
	/** Socket used to listen for incoming messages */
	private static ServerSocket ss = null;
	/** Port used by ss defined above.  */
	private static int portInUse = -1;
	/** TRUE if this peer is registered with the central server. FALSE otherwise */
	private static boolean registeredWithCentralServer = false;
	/** Original IP address of this peer as returned by the central server */
	private static String originalAddress = "";
	// Initialise the logger
	/** Logger used to print messages and errors on the
	 *  standard output (usually the console - configuration in config/logback.xml).
	 */
	private final static ch.qos.logback.classic.Logger log=
			(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Peer.class);
	/** Logger level. To decide which logging statements to be printed by the program.
	 *  By default set to INFO in production environment. During development, set to
	 *  TRACE
	 */
	// Comment the next line before packaging the application
	//private static String logLevel="TRACE";
	// Un-comment the next line while packaging the application
	private static String logLevel="INFO";

	// Central server identifiers
	/** IP address of the central server as supplied by the user */
	private static String csIP = "";
	/** Port used by the central server to listen for incoming messages */
	private static int csPort = -1;

	// Neighbour's identifiers
	/** Holds the IP of the neighbours. Maps IP:port to IP */
	private static HashMap<String, String> nbrIP = new HashMap<String, String>();
	/** Holds the ports of the neighbours. Maps IP:port to port */
	private static HashMap<String, Integer> nbrPort = new HashMap<String, Integer>();

	// Sockets
	/** Socket used to connect to the central server. */
	private static Socket cs;

	// public static Socket peerSocket;

	/**
	 * Runs the peer. Starting point for the program.
	 * 
	 * @param args Command line arguments
	 */
	public static void main(String args[]) {
		// Set the logger's level
		LoggerHelper.setLogLevel(logLevel, Peer.class);
		// print internal state of the logger. Comment the following two lines before
		// packaging the application for production.
	    //LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	   // StatusPrinter.print(lc);
	 // Get the details from command line arguments
		parseCommandLineArguments(args);
		log.info("Working directory: {}",workingDirectory);
		log.info("Central Server: {}",centralServer);

		try {
			// Once the command line arguments have been parsed, we are connected
			// to the central server (this happens to verify the information
			// about the central server).
			// Store the port being used to connect to the central server
			// We will use the same port to listen for other incoming
			// connections after we are done registering this peer on
			// the central server.
			portInUse = cs.getLocalPort();
			// Let the user know we are connected to the central server
			log.info("Connected to central server using port {}",portInUse);
			// Connected? Cool.
			// Let's not waste this connection and register the peer already!
			registerPeer();
			// We will keep listening for other incoming messages, yeah?
			// Gotta open up a socket for that.
			startLocalSocketToListenForCommands();

			// Obviously the user wants to interact with this
			// beautiful piece of code that is the peer program.
			// Should we allow that?
			// Oh sure. We have no choice really. Being a requirement and all
			// that.
			// Alright here goes nothing...........
			// Command processing starts here!
			// Wait, On second thought, let's outsource it out to another
			// method.
			// keep it clean like that.
			commandProcessing();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parses, verifies and stores the command line arguments.
	 * <p>
	 * The first argument will always be the working directory.
	 * <p>
	 * The second argument will always be the address of the central
	 * server.
	 * <p>
	 * Optional arguments include:
	 * <ul>
	 * <li> -p<port> : Specify the port the program will use to listen for messages.
	 * <li> -l<Logging level> : Specify the level of logging to be used. Can be any 
	 * of the following - TRACE, DEBUG, INFO
	 * </ul>
	 * @param args
	 *            Command line arguments
	 */
	private static void parseCommandLineArguments(String[] args) {
		log.trace("parseCommandLineArguments - {}",args);
		// Check if the user has supplied any optional arguments
		if (args.length>=3) {
			// Traverse through all the supplied arguments
			for(int i=2;i<args.length;i++) {
				// Process the current argument
				String arg=args[i];
				// Check if user has specified the logger level
				if(arg.substring(0,2).equals("-l")) {
					// Looks like they have.
					// Get the level they want to use.
					String level=arg.substring(2).trim();
					// Set the required class member.
					logLevel=level;
					LoggerHelper.setLogLevel(logLevel, Peer.class);
					log.trace("logLevel set to {} by user",logLevel);
					log.debug("logLevel:{}",logLevel);
				} else if (arg.substring(0,2).equals("-p")) {
					// User wants to specify the port that will be used to listen
					// for messages.
					int suppliedPort=Integer.parseInt(arg.substring(2).trim());
					setPortInUse(suppliedPort);
				}
				
			}
		}
		// Check if user has supplied the working directory
		if (args.length >= 1) {
			// They have. Set the required class member.
			workingDirectory = args[0].trim();
			log.trace("Working directory set to {} by user",workingDirectory);
			log.debug("workingDirectory:{}",workingDirectory);
			// Verify if the directory exists.
			verifyWorkingDirectory();
		}
		// Check if user has supplied the address of the central server
		if (args.length >= 2) {
			// They have. Set the required class member.
			centralServer = args[1].trim();
			log.trace("Central server address set to {} by user",centralServer);
			log.debug("centralServer:{}",centralServer);
			// Connect to the central server. Doing this also helps us
			// verify if we can connect to the server.
			if(connectToCentralServer()){
				log.trace("Connected to the central server.");
				log.debug("cs:{}",cs);
			} else {
				log.trace("Invalid central server.");
				log.info("A valid central server needs to be supplied to continue using the program. Exiting.");
				quit();
			}
		}
	}

	/**
	 * Sets the port to be used to listen for messages and
	 * connect to the central server while registering.
	 * 
	 * @param suppliedPort The port number to set
	 */
	private static void setPortInUse(int suppliedPort) {
		log.trace("setPortInUse {}",suppliedPort);
		portInUse=suppliedPort;
		log.trace("portInUse set to {} by user",portInUse);
		log.debug("portInUse:{}",portInUse);
		
		if(!isPortAvailable(portInUse)) {
			log.info("The specified port is not available. Exiting.");
			quit();
		} else {
			log.debug("Specified port is available. Carry on.");
		}

	}
	

	/**
	 * Checks if the port is available
	 * 
	 * @param portInUse2 The port whose availability needs to be checked.
	 * 
	 * @return TRUE if port is available; FALSE otherwise.
	 */
	private static boolean isPortAvailable(int portInUse2) {
		log.trace("isPortAvailable {}",portInUse2);
		boolean available=false;
		
		Socket s=null;
		try {
			s=new Socket("localhost",portInUse2);
		} catch (UnknownHostException e) {
			log.debug("Error occurred while trying to connect to port. Available.");
			available=true;
		} catch (IOException e) {
			log.debug("Error occurred while trying to connect to port. Available.");
			available=true;
		} finally {
			if(null!=s) {
				if(s.isConnected() || !s.isClosed()) {
					try {
						s.close();
					} catch (IOException e) {
						log.error("Error occurred while trying to close temp socket.",e);
					}
				}
			}
		}
		
		return available;
	}

	/**
	 * Verifies if the working directory is valid. If not, asks the user to
	 * specify a new path. Or if they would like to create the directory. Or if
	 * they had rather damn the whole thing and exit.
	 */
	private static void verifyWorkingDirectory() {
		log.trace("verifyWorkingDirectory {}",workingDirectory);
		File wd = new File(workingDirectory);
		if (!wd.isDirectory()) {
			log.info("Invalid working directory.");
			log.info("Would you like to: ");
			log.info("1. Create the specified directory ({})",workingDirectory);
			log.info("2. Specify a new path.");
			log.info("3. Exit");
			log.info("Enter choice (1 or 2): ");
			Scanner s = new Scanner(System.in);
			int c = s.nextInt();
			log.debug("i/p - {}",c);
			switch (c) {
			case 1:
				wd.mkdirs();
				break;
			case 2:
				log.info("Please enter a new path: ");
				workingDirectory = s.next();
				break;
			case 3:
				quit();
				break;
			}
			verifyWorkingDirectory();
		} else {
			log.trace("{} is a valid directory",workingDirectory);
		}
	}

	/**
	 * <p>Register the peer on the central server and find out what the central
	 * server has to say about that.</p>
	 * 
	 * <p>Usually, in response, we will either get a confirmation message or we
	 * will get a confirmation message AND the address of a randomly assigned
	 * peer. How cool is THAT?!</p>
	 * 
	 * @throws IOException
	 */
	private static void registerPeer() throws IOException {
		// Register with the server. Also let the user knows what's happening.
		log.info("Registering..");
		PrintWriter pw = new PrintWriter(cs.getOutputStream(), true);
		// Let the central server we want to register.
		log.trace("Sending command: {}",MessageCommands.PEER_WANTS_TO_CONNECT);
		pw.println(MessageCommands.PEER_WANTS_TO_CONNECT);
		log.trace("command sent");
		// pw.close();
		// Let's see what the central user has to say
		BufferedReader br = new BufferedReader(new InputStreamReader(
													cs.getInputStream()));
		String message = "";
		while ((message = br.readLine()) != null) {
			if (message.trim().equalsIgnoreCase(
					MessageCommands.PEER_REGISTERED_CONFIRMATION)) {
				// Alright, registration was successful. Let the user know.
				log.info("Registered on server.");
			} else if (message.trim().equalsIgnoreCase(
					MessageCommands.SENDING_ORIGINAL_ADDRESS)) {
				// The server is sending out this Peer's original address. For
				// future reference.
				message = br.readLine();
				originalAddress = message;
				log.info("My address is {}",originalAddress);
			} else {
				// The server has (most probably) sent the address of the
				// randomly assigned neighbour.
				// To start off, we will let the user know about that.
				log.info("Assigned neighbour is {}",message);
				// Add it to the list of neighbours.
				neighbours.add(message);
				log.debug("neighbours:{}",neighbours);
				// Parse and extract IP and port of the neighbour.
				if (defineNeighbour(message)) {
					// Go say Hi! and welcome them to the neighbourhood.
					sayHi(message);
				}
			}
		}
		pw.close();
		cs.close();
		registeredWithCentralServer = true;
		cs = null;
	}

	/**
	 * <p>As soon as the peer is registered with the central server, the user can
	 * command it to do anything they like. Anything.</p>
	 * 
	 * <p>Lol. JK. Only the following commands are supported.</p>
	 * 
	 * <p>1. quit - quits the program. Duh.</p>
	 * <p>2. get filename - searches for the specified file on the n/w and downloads it.</p>
	 * <p>3. share filename peer - Sends the specified file to the specified user.</p>
	 * <p>4. list - lists all files in the working directory.</p>
	 */
	private static void commandProcessing() {
		log.trace("commandProcessing");
		Scanner s = new Scanner(System.in);
		String command = "";
		while ((command = s.nextLine().trim()) != null) {
			log.debug("command:{}",command);
			// A whitespace in the command means that
			// there are two parts to the command.
			// The command itself and the parameter
			// being passed along with that.
			// So we check what type of command it is,
			Pattern pattern = Pattern.compile("\\s");
			Matcher matcher = pattern.matcher(command);
			boolean found = matcher.find();
			if (found) {
				log.debug("Command contains whitespace");
				// So, the command does have a whitespace
				// We will have to split up the command
				// and its parameter so we can do
				// as the user wants us to do.
				StringTokenizer st = new StringTokenizer(command, " ");
				String cmdToken = st.nextToken();
				if (cmdToken.equalsIgnoreCase("get")) {
					log.debug("get");
					// Extract the filename from the command.
					String fileName = null;
					try {
						// Store the file name.
						fileName = st.nextToken();
					} catch (Exception e) {
						log.error("Error occurred while extracting filename. Check and try again later.",e);
					}
					// Search and get that file.
					// NOW!
					log.info("Retrieving {}",fileName);
					getFile(fileName);
				} else if (cmdToken.equalsIgnoreCase("share")) {
					log.debug("share");
					// Or send a file to another peer
					// on the network.
					// First, extract rest of the details from the command.
					String fileName = null;
					String peer = null;
					try {
						// Then store the file name.
						fileName = st.nextToken();
						peer = st.nextToken();
					} catch (Exception e) {
						log.error("Error occurred while extracting details. Check and try again later.",e);
					}
					log.info("Sharing file {} with peer {}",fileName,peer);
					shareFile(fileName, peer);
				}
			} else {
				if (command.equals("quit")) {
					log.debug("quit");
					// The user does not need us any longer.
					// Time to say goodbye.
					// No crying.
					// I hate tears.
					quit();
				} else if (command.equals("list")) {
					log.debug("list");
					// The user's a little daft and forgets what
					// files they stored in the working directory.
					// Let's list out all the files in the
					// working directory they specified earlier.
					listFiles();
				}
			}

		}
		s.close();
	}

	/**
	 * <p>Informs the neighbour that they have been assigned to us so that they
	 * know we exist and can consider us as a neighbour too.</p>
	 * 
	 * <p>Hopefully, they are excited to see us.</p>
	 * 
	 * <p>Basically sending a predefined message. And then the port for extra
	 * measure.</p>
	 * 
	 * <p>Oh and we are doing this on a new thread, so that no other operations are
	 * affected. Too cool for school? Aw yeah!</p>
	 * 
	 * @param message String that specifies the neighbour we want to connect to. Should be in the IP:port format.
	 */
	private static void sayHi(String message) {
		log.trace("sayHi {}",message);
		// We will need to use a final identifier.
		// Because JAVA.
		final String finalMsg = message;
		// Now define the thread and what it does.
		Thread t = new Thread() {
			@Override
			public void run() {
				log.trace("Starting another thread to accomplish the task of informing assigned neighbour.");
				// Connect to the neighbour and get the socket
				log.trace("Connecting to neighbour.");
				Socket nbrSocket = connectToNeighbour(finalMsg);
				log.debug("nbrSocket:{}",nbrSocket);
				// Let the user know what's happening
				log.info("Informing assigned neighbour.");
				boolean retry = false;
				PrintWriter pw = null;
				// We will let this happen without any interaction
				// with the user.
				// This will basically go on till the neighbour has been
				// successfully informed or till 20 attempts. After that we
				// will give up. Because, what's the point, really?
				int attempts = 0;
				do {
					try {
						attempts++;
						pw = new PrintWriter(nbrSocket.getOutputStream());
						log.debug("pw:{}",pw);
						// Send a message to welcome them
						log.trace("Sending command: {}",MessageCommands.PEER_NEIGHBOUR_WELCOME);
						pw.println(MessageCommands.PEER_NEIGHBOUR_WELCOME);
						log.trace("Command sent.");
						// Send the port where we would be more than happy to
						// let them connect with us.
						int portToSend=getLocalPortInUse();
						log.trace("Sending port that we use {}",portToSend);
						pw.println(portToSend);
						log.trace("Port sent.");
					} catch (IOException e) {
						log.error("Error occurred while informing neighbour.",e);
						retry = true;
						// a quick nap to delay the next attempt
						try {
							log.trace("Suspending thread.");
							sleep(attempts * 2 * 1000);
						} catch (InterruptedException e1) {
							log.error("Thread was interrupted while asleep. That's nasty bro.",e);
						}
						log.debug("attempts:{}",attempts);
					} finally {
						// Performing cleanup
						log.trace("Cleaning up resoureces.");
						if (null != pw) {
							log.trace("Closing PrintWriter {}",pw);
							pw.close();
						}
					}
				} while (retry && attempts < 20);
				

			}
		};
		// And start the thread!
		log.trace("Starting thread to inform neighbour.");
		log.debug("t:{}",t);
		t.start();
	}

	/**
	 * Gives the port being used to listen for new connections and messages.
	 * 
	 * @return The local port that is being used to listen for incoming
	 *         connections.
	 */
	private static int getLocalPortInUse() {
		log.trace("getLocalPortInUse");
		if (portInUse == -1) {
			if (null != ss) {
				if (!ss.isClosed()) {
					log.trace("Getting local port from ServerSocket ss.");
					portInUse = ss.getLocalPort();
				}
			} else if (null != cs) {
				if (!cs.isClosed()) {
					log.trace("Getting local port from Central server socket ss.");
					portInUse = cs.getLocalPort();
				}
			}
		}
		log.debug("portInUse:{}",portInUse);
		return portInUse;
	}

	/**
	 * Starts a new thread that opens up a new socket used to listen for
	 * incoming connections and accept them.
	 */
	private static void startLocalSocketToListenForCommands() {
		log.trace("startLocalSocketToListenForCommands");
		log.trace("Trying to close the socket used to connect to the central server.");
		// We will have to close the socket that was used to connect to the central server
		// because it used the same port that will be used to listen for incoming commands
		// and messages.
		if (null != cs) {
			if (cs.isClosed() == false) {
				try {
					cs.close();
					log.trace("Socket closed {}",cs);
				} catch (IOException e) {
					log.error("Unable to close socket.",e);
				}
			}
		}
		// First, define what the thread is supposed to do.
		Thread t = new Thread() {
			@Override
			public void run() {
				log.trace("Thread to listen for new messages/commands started.");
				boolean socketStarted = false;
				int attempt = 0;
				// Once the thread starts,
				// a port will be opened so that we
				// can listen for incoming connections.
				// We can try to keep doing this forever.
				// Until successful. But if unsuccessful, we
				// will try again after a short delay (which will
				// will increase with every attempt).
				do {
					attempt++;
					try {
						// Okay, start a socket and open the port.
						log.trace("Attempting to start socket to listen for commands.");
						ss = new ServerSocket(getLocalPortInUse());
						log.trace("Socket started successfully.");
						log.debug("ss:{}",ss);
						socketStarted = true;
						break;
					} catch (IOException e) {
						log.error("Unable to start the socket. Look at the stack trace for more details.",e);
					}
					// Unsuccessful attempt. That sucks.
					try {
						// A short nap is in order.
						log.trace("Suspending thread.");
						sleep(attempt * 2 * 1000);
					} catch (InterruptedException e) {
						log.error("Thread was interrupted while asleep. I get violent when my sleep is interrupted."
								+ "Fortunately for you, this Thread is non-violent.",e);
					}
					// ... and here we go again!
					log.info("Failed to open socket. Trying again..");
				} while (socketStarted == false);
				log.info("Listening for messages on {}",getLocalPortInUse());
				Socket incoming = null;
				try {
					log.trace("Waiting for incoming connections.");
					// Let's wait for incoming connections.
					while ((incoming = ss.accept()) != null) {
						// Whoa! Someone just connected with us.
						// I wonder what they are trying to say..
						// Let's find out!
						log.trace("Process the incoming message {}",incoming);
						processIncoming(incoming);
					}
					// This message will probably never get displayed.
					// But the programmer can do anything he likes.
					// He's cool like that.
					log.info("Tired of listening.");
				} catch (IOException e) {
					log.error("Error occurred while listening for messages. It's a selfish world.",e);
				}
			}
		};
		// Start the thread!
		log.trace("Starting thread to listen for new messages.");
		log.debug("t:{}",t);
		t.start();
	}

	/**
	 * <p>Processes any and all incoming messages that might be encountered.</p>
	 * 
	 * <p>If the messages mean something important to us (e.g. neighbour welcoming
	 * us or someone looking for a file), we will try and do whatever is
	 * expected.</p>
	 * 
	 * @param incoming Socket being used to listen for incoming messages
	 */
	private static void processIncoming(Socket incoming) {
		log.trace("processIncoming {}",incoming);
		BufferedReader br;
		try {
			// System.out.println("Going line by line");
			br = new BufferedReader(new InputStreamReader(
					incoming.getInputStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				log.debug("i/p:{}",line);
				if (line.equalsIgnoreCase(MessageCommands.PEER_NEIGHBOUR_WELCOME)) {
					// Someone just said HI!.
					log.trace("New neighbour assigned.");
					// Let's get their address
					line = br.readLine();
					log.debug("Before processing:{}",line);
					String theirAddress = incoming.getInetAddress().toString()
							.replaceAll("/", "").trim()
							+ ":" + line;
					log.debug("After processing:{}",theirAddress);
					// Tell the user about our new neighbour.
					// Although, they probably don't care.
					// But, good to know, y'know.
					log.info("New neighbour: {}",theirAddress);
					// Add that address to our list, so we remember our
					// neighbours.
					log.trace("Adding to list of neighbours.");
					neighbours.add(theirAddress);
					log.debug("Neighbours:{}",neighbours);
					defineNeighbour(theirAddress);
				} else if (line.equals(MessageCommands.LOOKUP_COMMAND)) {
					log.trace("Someone is looking for a file.");
					// This is a lookup request.
					String fileName = null;
					String ip = null;
					String port = null;
					String originalPort = null;
					String propagatingNbrIP = null;
					String originalPortOfNbr = null;

					// Let's get the rest of the information.
					// First up, the filename
					line = br.readLine();
					if (line != null) {
						fileName = line;
					}
					log.debug("fileName:{}",fileName);
					// Then, the IP.
					line = br.readLine();
					if (line != null) {
						// This might be the original requester of the file.
						// If that's the case, we will have to get the IP
						// from the socket object.
						if (line.equals(MessageCommands.ORIGINAL_REQUESTER)) {
							// Get the ip from the socket object
							log.trace("Extracting IP information.");
							ip = incoming.getInetAddress().getHostAddress();
						} else {
							// otherwise, the message already contains the
							// IP. We will use that
							ip = line;
						}
					}
					log.debug("ip:{}",ip);
					// Lastly, the port.
					line = br.readLine();
					if (line != null) {
						port = line;
					}
					log.debug("port:{}",port);
					// Get the original port
					if ((line = br.readLine()) != null) {
						originalPort = line;
					}
					log.debug("originalPort:{}",originalPort);
					// Get the lookup command propagating neighbour's IP
					// Get the ip from the socket object
					log.trace("Extracting propagating neighbour's IP.");
					propagatingNbrIP = incoming.getInetAddress()
												.getHostAddress();
					log.debug("propagatingNbrIP:{}",propagatingNbrIP);
					// And this neighbour's original port
					if ((line = br.readLine()) != null) {
						originalPortOfNbr = line;
					}
					log.debug("originalPortOfNbr:{}",originalPortOfNbr);
					log.trace(propagatingNbrIP+":"+originalPortOfNbr+" sent lookup message");
					// Check if we have the file that has been requested
					// if it does, we will let the requester know and
					// send them the file. If not, we will send out the
					// lookup message to our neighbours.
					log.info(ip + ":" + originalPort
							+ " is looking for " + fileName);
					if (fileExistsInWorkingDirectory(fileName)) {
						log.info("I have the file. Sending it.");
						sendFile(fileName, ip, port);
					} else {
						log.trace("Don't have that file.");
						propagateLookupMessage(fileName, ip, port,
								originalPort, propagatingNbrIP,
								originalPortOfNbr);
					}
				} else if (line.equals(MessageCommands.FILE_START)) {
					// Someone is attempting to send a file
					log.trace("Someone is attempting to send a file");
					// Get the file name
					log.info("Downloading file.");
					FileOutputStream fos = null;
					InputStream is = null;
					BufferedOutputStream bos = null;

					try {
						String fileName = br.readLine();
						log.debug("fileName:{}",fileName);
						log.info("Receiving file " + fileName
								+ " from "
								+ incoming.getInetAddress().getHostAddress()
								+ ":" + incoming.getPort());
						// Create a temporary file
						log.trace("Creating temporary file.");
						verifyWorkingDirectory();
						
						String tempFileName = workingDirectory + "/" + fileName
												+ "_temp";
						log.debug("tempFileName:{}",tempFileName);
						File file = new File(tempFileName);
						
						// Get the input stream
						is = incoming.getInputStream();
						// Get the stream's buffer size
						int bufferSize = incoming.getReceiveBufferSize();
						log.debug("bufferSize:{}",bufferSize);
						// Create the output stream
						// We will first write to a temporary file (created
						// earlier) and then rename
						// it to the specified name.
						fos = new FileOutputStream(file);
						bos = new BufferedOutputStream(fos);

						// Write the file
						byte[] bytes = new byte[bufferSize];
						int count;
						log.trace("Writing file.");
						while ((count = is.read(bytes)) > 0) {
							bos.write(bytes, 0, count);
						}

						// Rename the file
						log.trace("Renaming file.");
						file.renameTo(new File(workingDirectory + "/"
								+ fileName));
						log.info("File saved: {}",fileName);
					} catch (Exception e) {
						log.error("Unable to save file",e);
					} finally {
						// Perform the necessary cleanup
						log.trace("Performing cleanup of resources.");
						if (bos != null) {
							log.trace("Closing BufferedOutputStream.");
							bos.close();
						}
						if (null != fos) {
							log.trace("Closing FileOutputStream.");
							fos.close();
						}
						if (null != is) {
							log.trace("Closing InputStream.");
							is.close();
						}
					}
				} else if (line.equals(MessageCommands.BYE_NEIGHBOUR)) {
					log.trace("A neighbour is quitting the network.");
					// Get the original port
					line = br.readLine();
					String originalPort = line;
					log.debug("originalPort:{}",originalPort);
					// Get the IP
					String ip = incoming.getInetAddress().getHostAddress();
					log.debug("ip:{}",ip);
					// Remove the neighbour from our list
					log.trace("Removing neighbour from our list.");
					String neighbourAddress = ip + ":" + originalPort;
					log.debug("neighbourAddress:{}",neighbourAddress);
					neighbours.remove(neighbourAddress);
					log.debug("neighbours:{}",neighbours);
					// Inform the user
					log.info("Neighbour " + neighbourAddress
								+ " has quit the network");
				}
			}
		} catch (IOException e) {
			log.error("An error occurred while processing the incoming message. It's not your fault.",e);
		}
	}

	/**
	 * Sends the specified file to the specified IP (destination address) at the
	 * specified port.
	 * 
	 * @param fileName
	 *            Name of file to be sent.
	 * @param ip
	 *            IP address of the destination peer.
	 * @param port
	 *            Port in which the destination will be listening for the file.
	 */
	private static void sendFile(String fileName, String ip, String port) {
		log.trace("sendFile {},{},{}",fileName,ip,port);
		Socket s = null;
		File f = null;
		FileInputStream fr = null;
		BufferedInputStream br = null;
		PrintWriter pw = null;
		BufferedOutputStream bos = null;

		try {
			// Connect to the destination
			log.trace("Connecting to the peer.");
			s = new Socket(ip, Integer.parseInt(port));
			log.debug("connected to peer.s:{}",s);
			// Prepare file for reading
			log.trace("Prepare file for reading.");
			f = new File(workingDirectory + "/" + fileName);
			fr = new FileInputStream(f);
			br = new BufferedInputStream(fr);

			// Get ready to write to the host
			log.trace("prepare to write to host.");
			pw = new PrintWriter(s.getOutputStream());
			bos = new BufferedOutputStream(s.getOutputStream());

			// Let the destination peer know we are sending a file
			log.trace("Sending command:{}",MessageCommands.FILE_START);
			pw.println(MessageCommands.FILE_START);
			log.trace("Command sent.");
			// Send the file name
			log.trace("Sending filename:{}",fileName);
			pw.println(fileName);
			log.trace("filename sent.");
			// Send the file size
			int fileSize = fileName.length();
			log.trace("Send the file size:{}",fileSize);
			pw.println(fileSize);
			log.trace("File size sent.");
			log.trace("flush PrintWriter.");
			pw.flush();
			byte[] bytes = new byte[(int) fileSize];
			// Read and upload the file
			int count;
			log.trace("Uploading the file.");
			while ((count = br.read(bytes)) > 0) {
				// write file to the output stream
				bos.write(bytes, 0, count);
			}
			// File has been successfully sent.
			log.info("File ({}) successfully sent to {}:{}",fileName,ip,port);
		} catch (UnknownHostException e) {
			log.error("The supplied peer does not exist.",e);
		} catch (IOException e) {
			log.error("An error occurred while trying to send the file.",e);
		} finally {
			// Perform cleanup
			log.trace("Performing cleanup of resources.");
			if (null != s) {
				if (!s.isClosed()) {
					try {
						log.trace("Closing socket.");
						s.close();
					} catch (IOException e) {
						log.error("Error occurred while trying to close the socket. Don't you worry child.",e);
					}
				}
			}
			if (null != fr) {
				try {
					log.trace("Closing FileReader {}",fr);
					fr.close();
				} catch (IOException e) {
					log.error("Couldn't close FileReader.",e);
				}
			}
			if (null != br) {
				try {
					log.trace("Closing BufferedReader {}",br);
					br.close();
				} catch (IOException e) {
					log.error("Couldn't close BufferedReader.",e);
				}
			}
			if (null != pw) {
				log.trace("Closin PrintWriter {}",pw);
				pw.close();
			}
			if (null != bos) {
				try {
					log.trace("Closing BufferedOutputStream {}",bos);
					bos.close();
				} catch (IOException e) {
					log.error("Couldn't close BufferedOutputStream.",e);
				}
			}
		}
	}

	/**
	 * <p>Checks if the specified file exists in the working directory.</p>
	 * 
	 * @param fileName
	 *            Name of the file to be searched. Not case sensitive.
	 * @return TRUE if found; FALSE otherwise.
	 */
	private static boolean fileExistsInWorkingDirectory(String fileName) {
		log.trace("fileExistsInWorkingDirectory {}",fileName);
		for (String s : getListOfFilesInWorkingDirectory()) {
			if (s.equals(fileName)) {
				log.debug("file {} exists in working directory {}",fileName,workingDirectory);
				return true;
			}
		}
		return false;
	}

	/**
	 * Sends out the lookup message to all the neighbours of the current peer.
	 * 
	 * @param fileName
	 *            The name of the file being searched for.
	 * @param ip
	 *            IP address of the host looking for the file.
	 * @param port
	 *            The port on which the host is listening for incoming
	 *            connections to allow file downloads.
	 * @param originalPort
	 *            The port that the requesting peer uses to listen for incoming
	 *            messages
	 * @param propagatingNbrIP
	 *            IP address of the neighbour that sent the lookup message We
	 *            need this to avoid sending them back the IP.
	 * @param originalPortOfNbr
	 *            We need this to correctly identify the neighbour that sent the
	 *            current lookup message.
	 */
	private static void propagateLookupMessage(String fileName, String ip,
			String port, String originalPort, String propagatingNbrIP,
			String originalPortOfNbr) {
		log.trace("propagateLookupMessage {},{},{},{},{},{}",
					fileName
					,ip
					,port
					,originalPort
					,propagatingNbrIP
					,originalPortOfNbr);
		for (String neighbour : neighbours) {
			log.trace("propagating to {}",neighbour);
			String fullAddress = ip + ":" + originalPort;
			if (fullAddress.equals(neighbour)) {
				// Looks like this is the peer that sent the propagation request.
				log.trace("This is the original requester. No need to send lookup message.");
				// Let's go to the next neighbour.
				continue;
			}
			String lookupMsgFrom = propagatingNbrIP + ":" + originalPortOfNbr;
			if (lookupMsgFrom.equals(neighbour)) {
				// This is the peer that originally requested the file. 
				log.trace("This is the lookup sender. No need to send lookup message.");
				// Let's continue to the next neighbour
				continue;
			}
			Socket s = null;
			PrintWriter pw = null;
			boolean msgSent = false;
			int attempts = 0;
			do {
				try {
					attempts++;
					log.trace("Sending lookup message to neighbour {}",neighbour);
					// Connect to the the neighbour
					s = connectToNeighbour(neighbour);
					if (null == s) {
						log.debug("Unable to connect to neighbour {}",neighbour);
						return;
					}
					pw = new PrintWriter(s.getOutputStream());
					// Let it know we are looking for a file
					log.trace("Sending cmd: {}",MessageCommands.LOOKUP_COMMAND);
					pw.println(MessageCommands.LOOKUP_COMMAND);
					log.trace("Command sent");
					// Then the name of the file
					log.trace("Sending filename: {}",fileName);
					pw.println(fileName);
					log.trace("filename sent");
					if (ip.equalsIgnoreCase(MessageCommands.ORIGINAL_REQUESTER) == false) {
						// If an IP has been provided, send them the same
						log.trace("Sending requester IP:{}",ip);
						pw.println(ip);
						log.trace("IP sent");
					} else {
						// Then let it know that we are the original requester
						// of the file. Ideally we would have mentioned our
						// IP address here, but that is impossible to mention
						// since we don't know which interface is being used
						// or our own IP without using an external service.
						// Since we can't do that, we will use a workaround
						// and not mention our IP, but tell our neighbour
						// to be a good sport and figure out our IP address.
						// The neighbour can then modify the message
						// to reflect our IP address and pass it on..
						log.trace("Sending requester IP:{}",MessageCommands.ORIGINAL_REQUESTER);
						log.debug("This is the original requester of the file");
						pw.println(MessageCommands.ORIGINAL_REQUESTER);
						log.trace("IP sent");
					}
					// Mention the port that will be used to download
					// the file.
					log.trace("Sending port available for upload:{}",port);
					pw.println(port);
					log.trace("Port sent");
					// Mention the original port that is used by the peer to
					// accept messages. So that it can be correctly identified.
					log.trace("Sending original port of the requesting peer:{}",originalPort);
					pw.println(originalPort);
					log.trace("Requesting peer's original port sent");
					// Mention this peer's original port too, so that it can be
					// properly
					// identified and it does not get the lookup message back
					// from its neighbours
					log.trace("Sending this peer's original port:{}",portInUse);
					pw.println(portInUse);
					log.trace("This peer's original port sent");
					msgSent = true;
				} catch (UnknownHostException e) {
					log.error("Could not connect to neighbour. Are they down?",e);
					msgSent = false;
				} catch (IOException e) {
					log.error("Error occurred while communicating with neighbour.",e);
					msgSent = false;
				} finally {
					// Cleanup
					log.trace("Performing cleanup of resources.");
					if (null != pw) {
						pw.close();
						log.trace("Closed PrintWriter {}",pw);
					}
					if (null != s) {
						if (!s.isClosed()) {
							try {
								log.trace("Closing socket connected to neighbour {}",s);
								s.close();
							} catch (IOException e) {
								log.error("Don't blink. Blink and you're dead. Don't turn your back. Don't look away",e);
							}
						}
					}
				}
				// If the messages were not sent successfully, we will try again
				// only 20 times. We will give up after that. Each attempt will
				// be made after an incremental delay.
				if (msgSent == false) {
					log.debug("attempts:{}",attempts);
					if (attempts > 20) {
						// 20 attempts over, time to give up.
						log.trace("20 attempts done. Giving up.");
						break;
					}
					try {
						// Delay for a little while and try again.
						log.trace("Suspending thread.");
						Thread.sleep(attempts * 2 * 1000);
					} catch (InterruptedException e) {
						log.error("Thread interrupted while asleep. Uncool.",e);
					}
				}
			} while (msgSent == false);
		}

	}

	/**
	 * Print out a list of all the files in the working directory.
	 */
	private static void listFiles() {
		log.info("List of files in " + workingDirectory);
		// Print out the list of files
		log.info("Files:{}",getListOfFilesInWorkingDirectory());
		/*for (String fileName : getListOfFilesInWorkingDirectory()) {
			System.out.println(fileName);
		}*/
	}

	/**
	 * Get a sorted list of files in the working directory.
	 * <p>
	 * There was never a more self explanatory method name.
	 * 
	 * @return Sorted list of file names in the working directory.
	 */
	private static List<String> getListOfFilesInWorkingDirectory() {
		log.trace("getListOfFilesInWorkingDirectory {}",workingDirectory);
		// Initialise a new list.
		List<String> rv = new ArrayList<String>();
		// Populate that list by iterating through the items
		// in the specified directory.
		for (File f : new File(workingDirectory).listFiles()) {
			// Check if the "file" is actually a file.
			// We perform this check because it could be a directory
			// too. Ain't nobody got time for that!
			if (f.isFile()) {
				// It is a file! Cool, add it to the list.
				log.trace("File is a file (I know, crazy, right?!");
				log.debug("File name:{}",f.getName());
				rv.add(f.getName());
			} else {
				// We'll log that name, just to be sure we are not doing something stupid.
				log.debug("File name:{}",f.getName());
			}
		}
		// Sort that list of filenames. Because, we can!
		Collections.sort(rv);
		log.debug("rv:{}",rv);
		return rv;
	}

	/**
	 * Share the specified file with the specified peer.
	 * 
	 * @param fileName Name of file to be shared.
	 * @param peer Address of file to be shared. IP:port format
	 */
	private static void shareFile(String fileName, String peer) {
		log.trace("shareFile {},{}",fileName,peer);
		// First, we check if the file actually exists.
		// It would be quite impossible to share the file if
		// it does not exist.
		if (fileExistsInWorkingDirectory(fileName) == false) {
			// Dayamm, it does not exist. Inform the user and leave the method.
			log.info("Invalid file name. Check and try again later.");
			log.debug("fileName:{}",fileName);
			return;
		} else {
			// Looks like the file exists. 
			// We will have to extract the IP/hostname and the port of the supplied
			// peer.
			log.trace("Trying to make sense of the supplied peer address {}",peer);
			StringTokenizer st = new StringTokenizer(peer, ":");
			String ip="";
			int port=-1;
			boolean properPeer=false;
			try {
				// Get the IP
				if(st.hasMoreTokens()) {
					ip = st.nextToken();
				}
				log.debug("ip:{}",ip);
				// Get the port.
				if(st.hasMoreTokens()) {
					port = Integer.parseInt(st.nextToken());
				}
				log.debug("port:{}",port);
				properPeer=true;
			} catch (NumberFormatException e) {
				// Yikes! User supplied invalid peer. What gives?!
				log.error("There was some problem with the peer address supplied. Check and try again later",e);
			}
			if(properPeer) {
				// We have all the information we need.
				log.trace("A proper peer was supplied {}",peer);
				// Now, let's try and send that file.
				sendFile(fileName, ip, "" + port);
			}

		}
	}

	/**
	 * Sends out a message to all the neighbours and lets them know that this
	 * peer is looking for the specified file.
	 * 
	 * @param fileName
	 *            File name of the file the user wants to download
	 */
	private static void getFile(final String fileName) {
		log.trace("getFile {}",fileName);
		// A little bit of pre-processing.
		if (null == fileName) {
			// if the user did not specify any file name
			// we will need to ask them for the same.
			log.debug("fileName:{}",fileName);
			askForFileName(1);
		} else {
			// Even if the user has provided a filename,
			// we will need to make sure of a few things.

			// Now we will check for the following conditions:
			// 1. The file name should be length >=1. Because obviously.
			// 2. It isn't blank
			if (fileName.length() < 1 || fileName.equals("")) {
				// If any of the above conditions aren't satisfied
				// we will ask the user to provide a valid file name.
				log.debug("fileName:{}",fileName);
				askForFileName(1);
			}
		}

		// With that out of the way, let's get down to business.
		// First, we will create a new socket which we will use to
		// eventually download the file. This will be done on a new
		// thread, as required.
		// The following variable is a thread safe version of boolean
		// that will be used to store whether the requested file was 
		// successfully received. 
		final AtomicBoolean fileGot = new AtomicBoolean(false);
		Thread t = new Thread() {
			@Override
			public void run() {
				log.trace("Running new thread to start the socket to listen for files.");
				boolean socketStarted = false;
				ServerSocket ss = null;
				int attempts = 0;
				do {
					try {
						attempts++;
						// Start the socket and bind it to any free port that might
						// open
						log.trace("Starting new ServerSocket");
						ss = new ServerSocket(0);
						log.debug("ss:{}",ss);
						socketStarted = true;
					} catch (IOException e) {
						log.error("Error occurred while trying to start socket.",e);
						log.trace("Try to reconnect after short delay.");
						log.debug("attempts:{}",attempts);
						if (attempts > 20) {
							break;
						}
						try {
							log.trace("Suspending thread.");
							sleep(attempts * 2 * 1000);
						} catch (InterruptedException e1) {
							log.error("Thread was interrupted while asleep.",e);
						}
					}
				} while (socketStarted == false);

				if (socketStarted) {
					log.trace("Socket successfully started");
					final int listeningOn = ss.getLocalPort();
					log.debug("listeningOn:{}",listeningOn);
					// So the socket has been bound.
					// Time to send out start the lookup process
					// We do this by flooding the peers with a message
					// stating that we are looking for a specific file.
					propagateLookupMessage(fileName,
							MessageCommands.ORIGINAL_REQUESTER, ""
									+ listeningOn, "" + portInUse,
							MessageCommands.ORIGINAL_REQUESTER, "" + portInUse);
					// The message is out in the wild.
					// If any of the peers on the network do have the
					// file that we want, they will contact us and
					// set up a connection so that the file can be downloaded.
					// So we wait patiently to hear from them
					// but only for 5 seconds. We timeout after that.
					final ServerSocket tempSocketAssignment = ss;

					Thread listenerThread = new Thread() {
						public void run() {
							log.trace("New thread running to download file.");
							Socket incoming = null;
							try {
								log.info("Searching for file. Listening on {}",listeningOn);
								while ((incoming = tempSocketAssignment
										.accept()) != null) {
									log.info("File found.");
									fileGot.set(true);
									log.debug("fileGot",fileGot);
									processIncoming(incoming);
									/*
									 * Ignore the following lines. Was used for debugging
									 * during development.
									 * 
									 * BufferedReader br=new BufferedReader( new
									 * InputStreamReader
									 * (incoming.getInputStream())); String
									 * line=null; int c=0;
									 * while((line=br.readLine())!=null) {
									 * if(c>=2) { break; }
									 * System.out.println(line); c++; }
									 */
								}
							} catch (IOException e) {
								log.error("Error occurred while listening for messages.",e);
							}
						}
					};
					log.trace("Starting thread to listen for file and download it.");
					listenerThread.start();
					long endTime = System.currentTimeMillis() + 5000;
					log.debug("endTime,{}",endTime);
					while (listenerThread.isAlive() && fileGot.get()==false) {
						if (System.currentTimeMillis() > endTime) {
							// We have exceeded the required time of waiting for
							// the file. We will check if the file was received.
							// If yes, we will let the program continue to do its thing.
							// Otherwise we will shut down the thread that's waiting for the file.
							log.trace("Exceeded timeout.");
							if (fileGot.get() == false) {
								log.info("File request timed out.");
								log.trace("Trying to interrupt the listening thread {}",listenerThread);
								listenerThread.interrupt();
								break;
							}
						}
						try {
							// We will set the thread to sleep for a second.
							// After that, will check again if the timeout has
							// been exceeded.
							log.trace("Suspending thread.");
							sleep(1000);
						} catch (InterruptedException e) {
							log.error("Thread was interrupted while sleeping. Not a big deal, here.",e);
						}
					}
				} else {
					log.info("Unable to start a socket. Thus, unable to get file.");
				}
			}
		};
		log.trace("Start new thread to start the socket to listen for files. {}",t);
		t.start();
	}

	/**
	 * Asks the user to provide a file name since the one provided earlier was
	 * invalid.
	 * 
	 * @param c
	 *            Use 1 if you want to get a file.
	 */
	private static void askForFileName(int c) {
		log.trace("askForFileName");
		log.info("Oops! That filename is invalid.");
		log.info("Please enter the filename once again:");
		Scanner s = new Scanner(System.in);
		String fileName = s.next().trim();
		if (c == 1) {
			getFile(fileName);
		}
	}

	/**
	 * Helper method to connect to the specified neighbour.
	 * 
	 * @param neighbourAddress
	 *             Address of the neighbour in the form of IP:port.
	 * 
	 * @return Socket that is connected to the neighbour.
	 */
	private static Socket connectToNeighbour(String neighbourAddress) {
		log.trace("connectToNeighbour {}",neighbourAddress);
		if (nbrIP.containsKey(neighbourAddress) == false) {
			// Neighbour has not been parsed yet.
			// Let's do that.
			log.trace("Neighbour has not been parsed yet.");
			if (defineNeighbour(neighbourAddress) == false) {
				return null;
			}
		}
		String ip = nbrIP.get(neighbourAddress);
		int port = nbrPort.get(neighbourAddress);
		Socket socket = null;

		try {
			log.trace("Attempting to connect to neighbour.");
			socket = new Socket(ip, port);
			log.trace("Connected to neighbour {}",socket);
		} catch (UnknownHostException e) {
			log.error("Unable to connect to specified neighbour. Are they down?",e);
		} catch (IOException e) {
			log.error("Some error occurred while trying to connect to the neighbour. Take a look at stacktrace.",e);
		}

		return socket;
	}

	/**
	 * Parses the neighbour details sent by the central server.
	 * 
	 * The details are then stored in maps for easy access.
	 * 
	 * @return TRUE if details are successfully stored in a map. FALSE if there
	 *         was some error.
	 */
	private static boolean defineNeighbour(String neighbourAddress) {
		log.trace("defineNeighbour {}",neighbourAddress);
		// Check if this neighbour has already been defined
		// if yes, we don't need to parse the string.
		if (nbrIP.containsKey(neighbourAddress) == false
				&& nbrPort.containsKey(neighbourAddress) == false) {
			// If not, let's go ahead and do that.
			StringTokenizer st = new StringTokenizer(neighbourAddress, ":");
			// Store the IP
			nbrIP.put(neighbourAddress, st.nextToken());
			if (st.hasMoreTokens()) {
				// Store the port, if available
				nbrPort.put(neighbourAddress, Integer.parseInt(st.nextToken()));
			} else {
				// The port is necessary.
				log.info("Invalid neighbour. Port was not supplied.");
				// quit();
				return false;
			}
		} else {
			if (neighbourAddress.length() < 8 || neighbourAddress.contains(".")) {
				log.info("Invalid neighbour supplied.");
				log.debug("neighbourAddress:{}",neighbourAddress);
				// quit();
				return false;
			}
		}
		// The neighbour has been successfully defined.
		log.debug("nbrIP:{}",nbrIP);
		log.debug("nbrPort:{}",nbrPort);
		return true;
	}

	/**
	 * This method will quit the network and exit the program.
	 */
	private static void quit() {
		if (registeredWithCentralServer == false) {
			// We never connected with the central server
			// Let's just get out of here.
			log.trace("Never registered with the central server. Make a quick exit");
		} else {
			// This party is getting boring.
			// Let's tell the central server
			// and get the hell out of here.
			PrintWriter out = null;
			if (null == cs) {
				connectToCentralServer();
			} else {
				if (cs.isClosed()) {
					log.trace("Connection to central server closed. Trying to connect.");
					connectToCentralServer();
				}
				if (cs.isConnected() == false) {
					log.trace("Not connected to central server, but socket is not null. Trying to connect.");
					connectToCentralServer();
				}
			}
			try {
				if(null!=cs) {
					out = new PrintWriter(cs.getOutputStream(), true);
					// Let's tell them and get out already.
					log.info("Informing central server...");
					log.trace("Sending command to central server: {}",MessageCommands.PEER_WANTS_TO_DISCONNECT);
					out.println(MessageCommands.PEER_WANTS_TO_DISCONNECT);
					log.trace("command sent");
					// Send the original port that was used to connect to the
					// central server
					log.trace("Sending original port: {}",portInUse);
					out.println(portInUse);
					log.trace("Original port sent");
					log.info("Done informing central server");
				}
				// Let the neighbours know
				log.info("Informing neighbours...");
				informNeighboursIQuit();
				log.info("Done.");
				// Wait a second and a half before quitting
				try {
					log.debug("Suspending thread for {}ms",1500);
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					log.error("Thread was interrupted.",e);
				}
			} catch (IOException e) {
				log.error("Error occurred while sending messages",e);
			}
			// Some final cleanup
			log.trace("Performing cleanup");
			if(null!=out) {
				log.trace("Closing PrintWriter {}",out);
				out.close();
			} else {
				log.debug("PrintWriter for centralserver was never initialised.");
			}
			if(null!=cs) {
				if (!cs.isClosed()) {
					log.trace("Connection to central server is not closed.");
					try {
						log.trace("Closing connection to central server {}",cs);
						cs.close();
						log.trace("Connection to central server closed {}",cs);
					} catch (IOException e) {
						log.error("Error occurred while trying to close the connection to central server.",e);
					}
				}
			}
		}
		// Ha! We are out of here! LOSERS!
		log.info("Done.");
		log.info("Bye!");
		System.exit(0);
	}

	/**
	 * Informs neighbours that we are quitting the network so that they can
	 * update their list of neighbours.
	 */
	private static void informNeighboursIQuit() {
		log.trace("informNeighboursIQuit");
		for (String neighbour : neighbours) {
			log.trace("Informing neighbour {}",neighbour);
			boolean msgSent = false;
			int attempts = 0;
			PrintWriter pw = null;
			Socket s = null;
			do {
				try {
					s = null;
					pw = null;
					attempts++;
					// Connect to the the neighbour
					s = new Socket(nbrIP.get(neighbour), nbrPort.get(neighbour));
					log.trace("connected to neighnour {}",s);
					pw = new PrintWriter(s.getOutputStream());
					// Let it know we are quitting
					log.trace("Sending command {}",MessageCommands.BYE_NEIGHBOUR);
					pw.println(MessageCommands.BYE_NEIGHBOUR);
					log.trace("command sent");
					// Let them know the original port that they would have
					// used to connect to us. This will allow them to accurately
					// identify us.
					log.trace("sending original port {}",portInUse);
					pw.println(portInUse);
					log.trace("original port sent");
					msgSent = true;
				} catch (UnknownHostException e) {
					log.error("Unable to connect to neighbour. Maybe it's down?",e);
					msgSent = false;
				} catch (IOException e) {
					log.error("Some error occured while connecting or sending messages. See stacktrace for more info",e);
					msgSent = false;
				} finally {
					// Clean up
					if (null != pw) {
						log.trace("flushing {}",pw);
						pw.flush();
						log.trace("closing PrintWriter {}",pw);
						pw.close();
					}
					if (null != s) {
						log.trace("closing socket {}",s);
						if (!s.isClosed()) {
							try {
								s.close();
								log.trace("socket closed {}",s);
							} catch (IOException e) {
								log.error("An error occured while closing the socket.",e);
							}
						}
					}
				}
				if (msgSent == false) {
					log.trace("Attempting to reconnect to neighbour {}",neighbour);
					log.debug("attempts:{}",attempts);
					if (attempts > 20) {
						break;
					}
					try {
						log.trace("Suspending thread");
						Thread.sleep(attempts * 2 * 1000);
					} catch (InterruptedException e) {
						log.error("Thread was interrupted between attempts to reconnect.",e);
					}
				}
			} while (msgSent == false);
		}

	}

	/**
	 * Connects the peer to the central server
	 */
	private static boolean connectToCentralServer() {
		log.trace("connectToCentralServer {}",centralServer);
		boolean connected=false;
		if (csIP.length() <= 1) {
			log.trace("central server not 'defined'");
			defineCentralServer();
		}
		//if (null == cs) {
			try {
				if (csPort != -1) {
					log.info("Trying to connect to central server  {}:{}",csIP,csPort);
					if(portInUse!=-1 && !registeredWithCentralServer) {
						// This means that the user has specified a port that they want
						// the program to use to listen for new messages. We will use the
						// same port to connect to the central server while registering
						// to make sure the central server stores and sends this port to 
						// any peers that are assigned this peer as their neighbour.
						InetSocketAddress sa=new InetSocketAddress(portInUse);
						log.debug("sa:{}",sa);
						log.debug("resolved:{}",sa.isUnresolved());
						cs=new Socket(csIP, csPort, sa.getAddress(), portInUse);
						log.debug("cs:{}",cs);
						log.trace("connection to central server bound to port {}",portInUse);
					} else {
						cs = new Socket(csIP, csPort);
						log.debug("cs:{}",cs);
						log.trace("connected to central server({}:{})",csIP,csPort);
					}
				}
				connected=true;
			} catch (UnknownHostException e) {
				log.error("Invalid address. Are you sure you used the correct address?",e);
				connected=false;
			} catch (IOException e) {
				log.error("An error occurred while trying to connect to the central server.",e);
				connected=false;
			}
		//}
		if(!connected && !registeredWithCentralServer) {
			if(shouldReConnectToCentralServer()) {
				connected=connectToCentralServer();
			}
		}
		return connected;
	}

	/**
	 * Asks the user to confirm the host information given to the program and
	 * attempts to connect to the host again if given permission.
	 */
	private static boolean shouldReConnectToCentralServer() {
		log.trace("askToReConnectToCentralServer");
		boolean rv=false;
		// Get new details
		changeCentralServer();
		Scanner s;
		s=new Scanner(System.in);
		// Ask if the user wants to reconnect
		log.info("Reconnect to central server? (Y/N): ");
		String c = "";
		c=s.next();
		log.debug("c:{}",c);
		if (c.equalsIgnoreCase("Y")) {
			log.trace("User wants to try to re-connect.");
			rv=true;
		} else {
			log.trace("User does not want to try to re-connect.");
			rv=false;
		}
		return rv;
	}

	/**
	 * Method to change information about the central server via input from the user.
	 */
	private static void changeCentralServer() {
		log.trace("changeCentralServer");
		// Display the current information about the central server
		log.info("Current information:");
		// First the IP address/hostname of the central server
		log.info("Hostname/IP address: " + csIP);
		if (csPort != -1) {
			// The port for central server has been set
			// Display that.
			log.info("Port: " + csPort);
		} else {
			// The port has not been set. We cannot connect to the central server without that.
			log.debug("The central server port variable has not been set.");
			log.info("The port has not been set. Cannot connect to the central server without that information.");
		}
		// Ask if the user wants to change the current information
		log.info("Change? (Y/N):");
		Scanner s = new Scanner(System.in);
		// Take the user's choice.
		String c = s.next();
		// Take actions according to the user's choice.
		if (c.equalsIgnoreCase("Y")) {
			// First, take the IP
			log.info("Please enter IP: ");
			csIP = s.next();
			// Then the port.
			log.info("Please enter port: ");
			csPort = s.nextInt();
		} else if (c.equalsIgnoreCase("N")) {
			// If the user does not want to change the information
			// its cool with us. Except, if the peer has never registered
			// with the central server, that means it is pretty useless 
			// continuing with the program. So we just display a message and quit.
			if(!registeredWithCentralServer) {
				log.info("To run the program we need to connect to a valid central server. Exiting");
				quit();
			}
		}
		// Display the new information to the user.
		log.info("New information: ");
		log.info("Hostname/IP address: {}",csIP);
		if (csPort != -1) {
			log.info("Port: {}",csPort);
		}
	}

	/**
	 * <p>Breaks down the supplied central server's address
	 * into separate IP and port elements.</p> 
	 * <p>If no port is supplied, it assumes 5555 as the default.</p>
	 */
	private static void defineCentralServer() {
		log.trace("defineCentralServer {}",cs);
		StringTokenizer st = new StringTokenizer(centralServer, ":");
		csIP = st.nextToken();
		log.trace("Central server IP set to {}",csIP);
		if (st.hasMoreTokens()) {
			log.trace("Setting port supplied by user");
			csPort = Integer.parseInt(st.nextToken());
		} else {
			log.trace("Setting default port");
			csPort = 5555;
		}
		log.trace("Central server port set to {}",csPort);
		log.debug("csPort:{}",csPort);
		// csIP="0.0.0.0";
		// csPort=5555;
	}
}
