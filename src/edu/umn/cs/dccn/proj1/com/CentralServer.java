package edu.umn.cs.dccn.proj1.com;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import edu.umn.cs.dccn.proj1.util.LoggerHelper;
import edu.umn.cs.dccn.proj1.util.MessageCommands;

/**
 * <p>Creates a centralised server.</p>
 * <p>This server will be used by the various peers to
 * find neighbours.</p>
 * <p>A list of peers and their address will be held in
 * memory (by default) or in an external file as 
 * specified by the user.</p> 
 * 
 * @author Vivek Ranjan
 *
 */
public class CentralServer {
	
	// Variables related to configuration options.
	/** TRUE if the user wants a list of peers stored in a file. Set by -f */
	private static boolean dictionaryInFile=false;
	/** Location of the file that stores the list of peers */
	private static String dictionaryFileLocation="peerList.txt";
	/** Logger used to print messages and errors on the
	 *  standard output (usually the console - configuration in config/logback.xml).
	 */
	private final static ch.qos.logback.classic.Logger log=
			(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(CentralServer.class);
	/** Logger level. To decide which logging statements to be printed by the program.
	 *  By default set to INFO in production environment. During development, set to
	 *  TRACE
	 */
	// Comment the next line before packaging the application
	//private static String logLevel="TRACE";
	// Un-comment the next line while packaging the application
	private static String logLevel="INFO";
	
	// Variables that store information related to the peers
	/** List of all the peers on the network */
	private static List<String> peerAddresses=null; // Stores the IP:Port of all peers
	
	//Variables that store information related to the central server
	/** Port used by the server to listen for messages from peers. Set as 5555 by default */
	private static int port=5555;
	/** Socket that listens for commands. **/
	private static ServerSocket ss=null;

	/**
	 * Entry point for the program. Starts the central server and listens for messages
	 * and responds as required.
	 * 
	 * @param args Optional command line arguments
	 */
	public static void main(String args[]) {
		// Set the logger's level
		LoggerHelper.setLogLevel(logLevel, Peer.class);
		// print internal state of the logger. Comment the following two lines before
		// packaging the application for production.
		//LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		//StatusPrinter.print(lc);
		// Parse the optional arguments specified and create configuration rules.
		parseArgs(args);
		
		
		String currentPeerAddress;
		try {
			boolean serverCreated=false;
			// Create the server
			do {
				try {
					log.trace("Attempting to create the server.");
					ss=new ServerSocket(port);
					log.debug("ss:{}",ss);
					serverCreated=true;
				} catch (BindException e) {
					log.info("Not able to connect on port {}. Maybe server is already running?",port);
					log.info("Try another port? (Y/N)");
					Scanner s=new Scanner(System.in);
					String c=s.next();
					if(c.equalsIgnoreCase("Y")) {
						log.info("Enter new port:");
						port=s.nextInt();
						log.debug("port:{}",port);
						log.info("Trying port {}",port);
						log.trace("Closing Scanner.");
						s.close();
					} else {
						log.trace("User does not want to try another port. Exiting program.");
						quit();
					}
					
				}
			} while(!serverCreated);
			
			log.info("Listening for connections on {}",ss.getLocalPort());
			
			Socket incoming=null;
			// Wait for incoming connections
			log.trace("Waiting for incoming connections.");
			while((incoming=ss.accept())!=null) {
				log.debug("incoming:{}",incoming);
				currentPeerAddress = incoming.getInetAddress().getHostAddress()+":"+incoming.getPort();
				log.debug("currentPeerAddress:{}",currentPeerAddress);
				/*if(null!=peerAddresses) {
					if(peerAddresses.contains(currentPeerAddress)) {
						continue;
					}
				}*/
				if(incoming.isClosed()==false) {
					log.info("Connected to {}",currentPeerAddress);
					BufferedReader br=new BufferedReader(
											new InputStreamReader(incoming.getInputStream()));
					log.info("Waiting for a message...");
					String message="";
					boolean processingDone=false;
					while((message=br.readLine())!=null && processingDone==false) {
						log.debug("message:{}",message);
						// Check if peer is registering or quitting
						if(message.equalsIgnoreCase(MessageCommands.PEER_WANTS_TO_DISCONNECT)) {
							// Get the original port that was used to connect to the server
							int originalPort=Integer.parseInt(br.readLine());
							log.debug("originalPort:{}",originalPort);
							// Use this port to build the original IP:port combination that was
							// used to register the peer on this central server
							String originalAddress=incoming.getInetAddress().getHostAddress()+":"+originalPort;
							log.debug("originalAddress:{}",originalAddress);
							removePeer(originalAddress);
							processingDone=true;
						} else if(message.equalsIgnoreCase(MessageCommands.PEER_WANTS_TO_CONNECT)) {
							// Peer is registering
							log.info("Peer is trying to register.. Registering.");
							PrintWriter out=new PrintWriter(incoming.getOutputStream(), true);
							// If no peers are registered
							if(null==peerAddresses) {
								// Instantiate the list of peers
								log.trace("First peer. Creating new list of peers.");
								peerAddresses=new ArrayList<String>();
							}
							// >=1 peers are registered on the server
							// Send this peer a confirmation message.
							log.trace("Sending confirmation message:{}",MessageCommands.PEER_REGISTERED_CONFIRMATION);
							out.println(MessageCommands.PEER_REGISTERED_CONFIRMATION);
							log.trace("Confirmation message sent.");
							// Send this peer its original address, just because.
							log.trace("Sending the peer the address it used to connect to us.",MessageCommands.SENDING_ORIGINAL_ADDRESS);
							out.println(MessageCommands.SENDING_ORIGINAL_ADDRESS);
							log.trace("Command sent.");
							log.trace("Sending original address {}",currentPeerAddress);
							out.println(currentPeerAddress);
							log.trace("Address sent.");
							// Check if any other peers are already registered on the network.
							if(peerAddresses.size()>0) {
								// Pick a randomly chosen peer and assign it as a neighbour.
								log.info("Assigning a neighbour..");
								Random random=new Random();
								String peer="";
								int c=0;
								do {
									peer=peerAddresses.get(
											random.nextInt(peerAddresses.size()));
									c++;
								} while(peer.equalsIgnoreCase(currentPeerAddress) && c<5);
								// Send the address.
								log.trace("Sending address of the assigned neighbour {}",peer);
								out.println(peer);
								log.trace("Address sent.");
								log.info("Neigbour assigned. "+peer);
							}
							// Add the connecting peer's address to the list
							log.trace("Adding the peer to the list of peers.");
							peerAddresses.add(currentPeerAddress.trim());
							log.debug("peerAddresses:{}",peerAddresses);
							log.trace("Closing PrintWriter:{}",out);
							out.close();
							// If the list is to be maintained in a file, add the peer address there.
							if(dictionaryInFile) {
								log.trace("Adding peer to the list in the file.");
								File f=new File(dictionaryFileLocation);
								FileWriter fw;
								// If a list of peers already exists, then we append to it
								if(peerAddresses.size()>1) {
									log.trace("Appending to file.");
									fw=new FileWriter(f, true);
								} else {
									// Otherwise we create a new list of peers
									log.trace("Creating new file.");
									fw=new FileWriter(f);
								}
								log.debug("fw:{}",fw);
								// Write the address to the file
								log.trace("Writing address to file.");
								fw.write(currentPeerAddress + "\n");
								log.trace("Closing FileWriter {}",fw);
								fw.close();
								fw=null;
								f=null;
							}
							processingDone=true;
						}
						if(processingDone==true) {
							// The command has been processed.
							// We will now break out of this look and wait for another 
							// connection.
							log.trace("Processing of command done.");
							break;
						}
					}
					// After successful operations, we no longer need to be connected. So we disconnect.
					log.info("Disconnecting {}",currentPeerAddress);
					log.trace("Closing socket {}",incoming);
					incoming.close();
					log.trace("Closing BUfferedReader {}",br);
					br.close();
					incoming=null;
					// Now we wait for another connection
					log.trace("Waiting for another connection.");
				}
			}
		} catch (IOException e) {
			log.error("Some error occurred. Stack trace shall reveal more information.",e);
			quit();
		} finally {
			quit();
		}
	}

	/**
	 * Removes the peer from the list of peers maintained in 
	 * the memory and, if enabled, in the file.
	 * 
	 * @param originalAddress The original address of the Peer that needs to
	 * 							be removed. This needs to be the same as the
	 * 							address of the peer that was used to register
	 * 							with the central server.
	 */
	private static void removePeer(String originalAddress)	{
		log.trace("removePeer {}",originalAddress);
		// Peer is trying to disconnect
		log.info("{} quit from network",originalAddress);
		// Remove the peer address from the list of peers
		log.trace("Removing from list of peers.");
		peerAddresses.remove(originalAddress);
		log.debug("peerAddresses:{}",peerAddresses);
		// Remove address from file if that option is enabled
		if(dictionaryInFile) {
			log.trace("Removing peer from file too.");
			File f=new File(dictionaryFileLocation);
			log.debug("f:{}",f);
			// We will store all the addresses except for the current one in a temporary file
			log.trace("Creating temporary file.");
			File ft=new File(dictionaryFileLocation+"temp");
			log.debug("ft:{}",ft);
			BufferedReader fbr=null;
			FileWriter fw=null;
			String line="";
			FileReader fr = null;
			try {
				fr=new FileReader(f);
				fbr=new BufferedReader(fr);
				fw=new FileWriter(ft);
				// read through the list of peers line by line
				log.trace("Reading from original file and writing to temp file.");
				while((line=fbr.readLine())!=null) {
					log.debug("line:{}",line);
					// If the address is same as the current one, then skip
					if(line.equalsIgnoreCase(originalAddress)) {
						log.trace("Skipping peer that is being removed {}",originalAddress);
						continue;
					}
					// Write the line to the temporary file
					fw.append(line+"\n");
				}
				fw.close();
				fbr.close();
				// Delete the original file.
				log.trace("Deleting original file {}",f);
				if(f.delete()) {
					log.debug("File delted {}",f);
				}
				// Rename the temporary file to the original name
				log.trace("Renaming temp file {} to {}",ft,f);
				if(ft.renameTo(f)) {
					log.debug("Renaming successful.");
				}
				f=null;
				ft=null;
			} catch (FileNotFoundException e) {
				log.error("Specified file does not exist.",e);
			} catch (IOException e) {
				log.error("Error occurred while modifying file.",e);
			}
		}
	}

	/**
	 * Gracefully shuts down the program.
	 */
	private static void quit() {
		log.info("Shutting down..");
		try {
			if(null!=ss) {
				log.trace("Closing ss {}",ss);
				if(ss.isClosed()==false) {
					ss.close();
				}
			} else {
				log.debug("Server was never started.");
			}
		} catch (IOException e1) {
			log.error("Error occurred while shutting down Socket.",e1);
		} finally {
			log.info("Bye!");
			log.trace("Calling System.exit()");
			System.exit(0);
		}
	}

	/**
	 * Helper method to parse the optional
	 * input arguments and set the fields accordingly.
	 * <p>
	 * The following command line arguments can be used:
	 * <ul>
	 * <li> -f<path> : Stores the list of peers in the specified file
	 * <li> -p<port> : Server will listen for messages on the specified server. Default port is 5555
	 * </ul>
	 * 
	 * @param args Optional command line arguments used while running the program.
	 */
	private static void parseArgs(String[] args) {
		log.trace("parseArgs - {}",args);
		for(String opt:args) {
			if(opt.substring(0,2).equals("-l")) {
				// Looks like they want to set the logging level.
				// Get the level they want to use.
				String level=opt.substring(2).trim();
				log.debug("level:{}",level);
				// Set the required class member.
				logLevel=level;
				LoggerHelper.setLogLevel(logLevel, CentralServer.class);
				log.trace("logLevel set to {} by user",logLevel);
				log.debug("logLevel:{}",logLevel);
			} else if(opt.substring(0, 2).equals("-f")) {
				// The user wants to maintain the list of peers
				// in a file.
				log.trace("Enabling the option to maintain list of peer in file.");
				dictionaryInFile=true;
				if(opt.length()>2) {
					dictionaryFileLocation=opt.substring(2);
					log.debug("dictionaryFileLocation:{}",dictionaryFileLocation);
				}
			} else if (opt.substring(0, 2).equals("-p")) {
				log.trace("Setting port as per user instruction.");
				port=Integer.parseInt(opt.substring(2));
				log.debug("port:{}",port);
			}
		}
	}

}
