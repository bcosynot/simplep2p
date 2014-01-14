/**
 * 
 */
package edu.umn.cs.dccn.proj1.util;

/**
 * Holds constant strings that are basically shared 
 * command messages between server and peers.
 *  
 * @author Vivek
 *
 */
public class MessageCommands {
		
	// A few constants
	/** Sent by Peer to central server while registering */
	public static final String PEER_WANTS_TO_CONNECT="geronimo";
	/** Sent by Peer to central server while quitting the network */
	public static final String PEER_WANTS_TO_DISCONNECT="astalavista";
	/** Sent by central server to peer as confirmation for the registration process */
	public static final String PEER_REGISTERED_CONFIRMATION="registered";
	/** Sent by a peer to its assigned neighbour to inform them about the assignment */
	public static final String PEER_NEIGHBOUR_WELCOME="howdy";
	/** Sent by peer to its neighbours to perform the lookup service */
	public static final String LOOKUP_COMMAND="lookingforsomethingyo";
	/** Sent by a file requesting peer to its neighbour, along with the lookup command, 
	 * telling them to figure out the IP (as we don't know the IP for the 
	 * peer requesting the file)/
	 */
	public static final String ORIGINAL_REQUESTER="figuroutmyipplis";
	/** Sent by a source peer to the destination peer informing them about the start of a file */
	public static final String FILE_START="filestartshere";
	/** Peer informing its neighbours that it is quitting. */
	public static final String BYE_NEIGHBOUR="sayonara";
	/** Sent by the central server to let the peer know that it is 
	 * sending out the peer's IP address as the next message.
	 */
	public static final String SENDING_ORIGINAL_ADDRESS="terabaapchorhai";
}
