package ahmedemam.opportunistic_wifi_adhoc;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by ahmedemam on 4/28/15.
 */


public class ConnectionThread extends Thread {

    private static final byte ADVERTISEMENT = 0;
    private static final byte HELLO = 1;
    private static final byte RTT = 2;
    private static final byte DATA = 3;
    private static final byte RTT_ACK = 5;
    private static final byte OPP_NEG = 6;              //Bundle Negotiation
    private static final byte OPP_FILE_ACK = 7;         //Bundle ACK
    private static final byte OPP_REQUEST = 8;        //Bundle negotiation reply
    private static final byte OPP_META_DATA = 9;        //Bundle Meta Data

    private static final String TAG = "Connection_Manager";
    DataInputStream inputStream;
    DataOutputStream outputStream;
    boolean WifiDirectConn = false;
    Socket connection;
    boolean client;
    int device_Id = 0;
    BlockingQueue<ByteArrayOutputStream> buffer;
    long connectionEstablishmentDelay;
    MainActivity mainActivity;
    private void debug(String message) {
        MainActivity.debug(message);
    }
    private void log(String message) {
        MainActivity.log(message);
    }


    public ConnectionThread(MainActivity activity,
                            Socket conn, boolean client_tmp, boolean isWifiDirect) {
        connection = conn;
        client = client_tmp;
        WifiDirectConn = isWifiDirect;
        mainActivity = activity;
        mainActivity.connection_establishment_time_end = System.currentTimeMillis();
        connectionEstablishmentDelay = mainActivity.getConnection_establishment_time_end()
                - mainActivity.getConnection_establishment_time_start();

        try {
            inputStream = new DataInputStream(connection.getInputStream());
            outputStream = new DataOutputStream(connection.getOutputStream());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public ConnectionThread(MainActivity activity,
                            InputStream inStream, OutputStream outStream, boolean client_tmp,
                            int deviceID, boolean isWifiDirect){
        mainActivity = activity;
        client = client_tmp;
        WifiDirectConn = isWifiDirect;
        inputStream = new DataInputStream(inStream);
        outputStream = new DataOutputStream(outStream);
        device_Id = deviceID;

        mainActivity.connection_establishment_time_end = System.currentTimeMillis();
        connectionEstablishmentDelay = mainActivity.getConnection_establishment_time_end()
                - mainActivity.getConnection_establishment_time_start();

    }


    @Override
    public void run() {
        if (client && WifiDirectConn)
            Hello();
        buffer = new LinkedBlockingQueue<>();
        ReadingThread readingThread = new ReadingThread(inputStream, buffer, connection, client,
                device_Id, WifiDirectConn);
        WritingThread writingThread = new WritingThread(
                mainActivity,outputStream, buffer, connection, client,
                device_Id, WifiDirectConn, readingThread, connectionEstablishmentDelay);
        MainActivity.connections.put(device_Id, writingThread);
        readingThread.start();
        writingThread.start();
    }



    public String getIPaddress() {
        return connection.getInetAddress().getHostAddress();
    }

    private void Hello() {
        try {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);

            packetStream.writeInt(5);
            packetStream.writeInt(MainActivity.DEVICE_ID);
            packetStream.writeInt(device_Id);
            packetStream.writeByte(HELLO);
            packetStream.writeInt(MainActivity.DEVICE_ID);

            byte[] packetByte = packet.toByteArray();
            outputStream.write(packetByte, 0, packetByte.length);
            debug("Sending hello message to " + device_Id + "@" + getIPaddress());
            mainActivity.addRoute(device_Id, device_Id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Send_Message() {
        try {
            outputStream.writeByte(2);
            byte[] data_byte = ("I am " + MainActivity.DEVICE_ID).getBytes();
            int data_length = data_byte.length;
            outputStream.writeInt(data_length);
            outputStream.write(data_byte);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * This thread consumes the input stream and queues the coming messages in a queue for it to be
     * consumed by a consumer thread
     */
    private class ReadingThread extends Thread {
        DataInputStream inputStream;
        DataOutputStream outputStream;
        boolean WifiDirectConn = false;
        Socket connection;
        boolean client;
        int device_Id = 0;
        boolean engineOn = true;
        BlockingQueue<ByteArrayOutputStream> buffer;


        public ReadingThread(DataInputStream inStream, BlockingQueue<ByteArrayOutputStream> buf,
                             Socket conn, boolean client_tmp, int deviceID, boolean isWifiDirect ){
            inputStream = inStream;

            connection = conn;
            client = client_tmp;
            device_Id = deviceID;
            WifiDirectConn = isWifiDirect;
            buffer = buf;
        }

        @Override
        public void run() {
            while(engineOn){
                try {

                    int packetLength = inputStream.readInt();

                    //Packet length + 8 because i am gonna add two integers, packet length and node ID
                    ByteArrayOutputStream packet = new ByteArrayOutputStream(packetLength + 12);
                    DataOutputStream packetStream = new DataOutputStream(packet);
                    packetStream.writeInt(packetLength);

                    //Read the node ID which is a 4 byte integer and then consume the whole
                    //packet out of the input stream
                    byte[] rest_packet = new byte[packetLength + 8];
                    inputStream.readFully(rest_packet);
                    packetStream.write(rest_packet);

                    buffer.put(packet);
                    Log.i(MainActivity.TAG, "[READING] buffer size: " + buffer.size());

                } catch(EOFException e){
                    e.printStackTrace();
                    cancel();
                } catch (IOException e){
                    e.printStackTrace();
                    cancel();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel(){
            engineOn = false;

            try {
                inputStream.close();
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Consumes messages from the queue "buffer" and implement logic for device to device data transfer
     */
    public class WritingThread extends Thread {
        DataInputStream inputStream;
        DataOutputStream outputStream;
        boolean WifiDirectConn = false;
        Socket connection;
        boolean client;
        int device_Id = 0;
        boolean engineOn = true;
        BlockingQueue<ByteArrayOutputStream> buffer;
        ReadingThread readingThread;
        long t_initial_start = 0;
        long t_initial_end = 0;
        long t_initial = 0;

        long t_receive_start = 0;
        long t_receive_end = 0;
        long t_receive = 0;

        long connectionEstablishment = 0L;
        MainActivity mainActivity;
        Queue<Opp_Bundle> filesToSend;
        Queue<Opp_Bundle> filesToReceive;

        boolean gotNegotiation = false;
        boolean negotiated = false;
        public WritingThread(MainActivity activity,
                             DataOutputStream outStream, BlockingQueue<ByteArrayOutputStream> buf,
                             Socket conn, boolean client_tmp, int deviceID, boolean isWifiDirect,
                             ReadingThread associateThread, long connEstablishment){

            mainActivity = activity;
            outputStream = outStream;
            connection = conn;
            client = client_tmp;
            device_Id = deviceID;
            WifiDirectConn = isWifiDirect;
            buffer = buf;
            readingThread = associateThread;
            connectionEstablishment = connEstablishment;

            filesToSend = new LinkedList<>();
            filesToReceive = new LinkedList<>();
        }

        @Override
        public void run() {
            while (engineOn) {

                try {

                    if(device_Id != 0 && !negotiated){
                        start_opportunistic_negotiation();

                    }

                    Log.i(MainActivity.TAG, "[WRITING] buffer size: " + buffer.size());
                    ByteArrayOutputStream thePacketStream = buffer.take();
                    inputStream = new DataInputStream(new ByteArrayInputStream(thePacketStream.toByteArray()));

                    int packetLength = inputStream.readInt();
                    int sourceNodeID = inputStream.readInt();
                    int nodeID = inputStream.readInt();


                    debug("Packet length " + packetLength + " from " + sourceNodeID +" to " + nodeID);

                    //If this packet is sent to me
                    //Process the packet
                    if (nodeID == MainActivity.DEVICE_ID) {
                        byte tagByte = inputStream.readByte();
                        switch (tagByte) {

                            /*
                             *                     Advertisement Packet Structure
                             * -------------------------------------------------------------------------
                             * | Newly Introduced Node ID | IP address length | IP address of new node |
                             * -------------------------------------------------------------------------
                             */
                            //Advertisement packet
                            case ADVERTISEMENT:
                                int deviceId = inputStream.readInt();    //Newly Introduced Node ID
                                int dataLength = inputStream.readInt(); //IP address length
                                byte[] data_byte = new byte[dataLength];
                                inputStream.read(data_byte);            //IP address of new node
                                String address = new String(data_byte);
                                debug("Got advertisement of " + deviceId + " @ " + address);

//                                if (!MainActivity.connections.containsKey(deviceId)) {
//                                    MainActivity.WifiDirectClientThread thread =
//                                            new MainActivity.WifiDirectClientThread(mainActivity,deviceId, address);
//                                    thread.start();
//                                }
                                break;

                            //This is a hello message
                            case HELLO:

                                deviceId = inputStream.readInt();
                                debug("Received a hello message from "+deviceId);
                                device_Id = deviceId;

                                //If I am the group owner advertise the new member to old
                                //group members
                                if (MainActivity.isGroupOwner) {
                                    advertiseNewConnection(deviceId, getIPaddress());
                                }
                                mainActivity.addRoute(device_Id, device_Id);
                                MainActivity.connections.put(deviceId, this);
                                break;

                            //RTT calculation packet
                            case RTT:

                                int sourceID = inputStream.readInt();
                                dataLength = inputStream.readInt();
                                data_byte = new byte[dataLength];
                                inputStream.readFully(data_byte);
                                String fileName = new String(data_byte);
                                int destID = inputStream.readInt();
                                debug("Received time sync packet from "+sourceID+" to send file " +
                                        fileName+" to "+destID);
                                ack_timeSync_packet(sourceID, fileName, destID);
                                break;

                            //Data packet
                            case DATA:
                                byte isOpportunistic = inputStream.readByte(); //is opportunistic

                                /*
                                 *                              Data Packet Structure
                                 * -------------------------------------------------------------------------------
                                 * | is opportunistic | Length of FileName | FileName | File Size (Bytes) | Data |
                                 * -------------------------------------------------------------------------------
                                 */
                                dataLength = inputStream.readInt();         //File name length
                                data_byte = new byte[dataLength];
                                inputStream.readFully(data_byte);           //File name in bytes
                                fileName = new String(data_byte);           //File name as a string
                                dataLength = inputStream.readInt();         //File Size


                                debug("Getting file packet for " + nodeID + " through " + device_Id +
                                        " fileName:" + fileName + " of size " + dataLength +
                                        "| Packet Length: " + packetLength);
                                /*
                                This is the size of the header of the data packet
                                It consists of one tag byte, 1 is opportunistic byte,
                                4 bytes for filename length, filename length bytes, 4 bytes for file length
                                */
                                int consumedBytes = 1+1+4+data_byte.length+4;

                                File f = new File((MainActivity.rootDir + fileName));
                                if(!f.exists()){
                                    log("Receiving file:" + fileName);
                                    if(isOpportunistic == 1) {
                                        Opp_Bundle theBundle = mainActivity.getBundle(fileName);
                                        debug(theBundle.toString());
                                        theBundle.set_start_recv_time(System.currentTimeMillis());
                                    }
                                }

                                FileOutputStream fileOutputStream = new FileOutputStream(f, true);

                                byte[] fileData = new byte[packetLength - consumedBytes];

                                inputStream.readFully(fileData);        //DATA of file

                                fileOutputStream.write(fileData);
                                fileOutputStream.close();


                                //I received the whole file
                                if(f.length() == dataLength){
                                    t_receive_end = System.currentTimeMillis();
                                    debug("Received the whole file");
                                    log("File " + fileName + " is received");

                                    //If this is an opportunistic data packet
                                    if(isOpportunistic == 1) {


                                        Opp_Bundle theBundle = mainActivity.getBundle(fileName);
                                        theBundle.set_end_recv_time(System.currentTimeMillis());

                                        //Edit data transfer duration
                                        long transferTime = theBundle.getEnd_recv_time() -
                                                theBundle.getStart_recv_time();

                                        debug("Transfer took "+transferTime);
                                        theBundle.setDuration(theBundle.getStart_recv_time(),
                                                theBundle.getEnd_recv_time());

                                        //Remove this bundle from the bundles to be received queue
                                        filesToReceive.remove(theBundle);

                                        /*
                                         * Check for file sanity
                                         */
                                        String checkSum = mainActivity.hashFile(fileName, "MD5");
                                        if(checkSum.equals(theBundle.getCheckSum())){
                                            debug("File was sent correctly");
                                        }
                                        else{
                                            debug("File wasn't sent in the right manner");
                                        }

                                        //Send data successfully transferred ACK
                                        send_opp_file_ack(fileName);
                                    }

                                    t_receive = t_receive_end - t_receive_start;
                                }

                                break;

                            //Connection Terminating Packet
                            case 4:

                                debug("Received a terminating packet");
                                t_receive_end = System.currentTimeMillis();
                                t_receive = t_receive_end - t_receive_start;

                                terminateConn();

                                break;

                            //ACK for RTT calculation
                            case RTT_ACK:

                                dataLength = inputStream.readInt();
                                data_byte = new byte[dataLength];
                                inputStream.readFully(data_byte);
                                fileName = new String(data_byte);
                                destID = inputStream.readInt();
                                t_initial_end = System.currentTimeMillis();
                                t_initial = (t_initial_end - t_initial_start) / 2;
                                debug("Received an ack for time sync packet will send file "+
                                        fileName+" to "+destID);
                                writeFile(destID, fileName);

                                break;

                            //Negotiating opportunistic oppBundles_repo
                            case OPP_NEG:

                                debug("[OPPORTUNISTIC] Got Negotiation");
                                int num_bundles = inputStream.readInt();
                                process_opportunistic_negotiation(num_bundles);

                                break;

                            //ACK for opportunistic file
                            case OPP_FILE_ACK:

                                dataLength = inputStream.readInt();
                                data_byte = new byte[dataLength];
                                inputStream.readFully(data_byte);
                                fileName = new String(data_byte);

                                debug("[OPPORTUNISTIC] Got ACK for "+fileName);
                                Opp_Bundle newBundle = mainActivity.getBundle(fileName);
                                if(newBundle.getDestination() == device_Id){
                                    newBundle.delivered();
                                }
                                filesToSend.remove(newBundle);
                                writeOppFile();
                                break;

                            case OPP_REQUEST:
                                debug("[OPPORTUNISTIC] Got Negotiation reply");
                                num_bundles = inputStream.readInt();
                                process_opportunistic_negotiation_reply(num_bundles);

                                gotNegotiation = true;

                                break;

                            case OPP_META_DATA:
                                debug("[OPPORTUNISTIC] Got Negotiation meta-data");
                                process_metadata();
                                break;

                            default:
                                debug("Not a valid tag");
                                break;
                        }
                    }
                    //If this packet is not sent to me
                    //Send to the node
                    else{
                        ByteArrayOutputStream packet = new ByteArrayOutputStream();
                        DataOutputStream packetStream = new DataOutputStream(packet);
                        packetStream.writeInt(packetLength);
                        packetStream.writeInt(MainActivity.DEVICE_ID);
                        packetStream.writeInt(nodeID);

                        byte[] rest_packet = new byte[packetLength];
                        inputStream.readFully(rest_packet);
                        packetStream.write(rest_packet);

                        int routeNode = MainActivity.routePacket(nodeID);   //Who should I forward this packet to
                        debug("Sending packet from " + device_Id + " to " + nodeID + " through " + routeNode);


                        if(routeNode == -1){
//                            opportunisticNode(nodeID, packet.toByteArray());
                        }
                        else {

                            writeToNode(routeNode, packet.toByteArray());
                        }
                    }

                    /*
                    If I am done negotiating and I have sent and received all the bundles, terminate
                    this connection
                     */
                    if(gotNegotiation && negotiated
                            && filesToReceive.size() == 0 && filesToSend.size() == 0){

                        terminateConn();
                    }
                }
                catch (IOException e) {

                    e.printStackTrace();
                    return;
                } catch (InterruptedException e) {
                    debug("Got an interrupted error");
                    e.printStackTrace();
                }
            }
        }

        /**
         * This function produce the negotiation packet for opportunistic file transfer
         */
        public void start_opportunistic_negotiation(){

            try {
                ByteArrayOutputStream packet = new ByteArrayOutputStream();
                DataOutputStream packetStream = new DataOutputStream(packet);

    /*
     *                Opportunistic Negotiation packet format
     * --------------------------------------------------------------------
     * |Packet Length (int)|Source Node ID (int)|Destination Node ID (int)|
     * --------------------------------------------------------------------
     * | Opp neg TAG BYTE (byte) | Number of bundles in negotiation (int) |
     * --------------------------------------------------------------------
     * | isDelivered |              Bundle ID 1                           |
     * --------------------------------------------------------------------
     * | isDelivered |              Bundle ...                            |
     * --------------------------------------------------------------------
     */

    /*
     *                                          Bundle Meta-data
     * ----------------------------------------------------------------------------------------------
     * | File name length (int) | File name (bytes) | checksum length (int) | checksum (bytes)      |
     * ----------------------------------------------------------------------------------------------
     * | Source node ID (int) | Dest node ID (int) | connection delay (long) | waiting delay (long) |
     * ----------------------------------------------------------------------------------------------
     * |                                 Number of intermediate nodes (int)                         |
     * ----------------------------------------------------------------------------------------------
     * |                                 Intermediate Nodes (ints)                                  |
     * ----------------------------------------------------------------------------------------------
     * |                                 File transfer duration (longs)                             |
     * ----------------------------------------------------------------------------------------------
     * |                                 Connection Establishment delay (longs)                     |
     * ----------------------------------------------------------------------------------------------
     * |                                 Waiting delay (longs)                                      |
     * ----------------------------------------------------------------------------------------------
     */
                //==========Start of Packet data===============
                packetStream.write(OPP_NEG);        //Opp neg TAG BYTE
                packetStream.writeInt(mainActivity.oppBundles_repo.size());  //Number of bundles in negotiation

                debug("Negotiating " + mainActivity.oppBundles_repo.size() + " bundles");
                for (int i = 0; i < mainActivity.oppBundles_repo.size(); i++) {

                    Opp_Bundle thebundle = mainActivity.oppBundles_repo.get(i);

                    byte[] filename_bytes = thebundle.getFileName().getBytes();

                    if(thebundle.isDelivered()){
                        packetStream.writeByte(1);
                    }
                    else{
                        packetStream.writeByte(0);
                    }

                    packetStream.writeInt(filename_bytes.length); //File name length
                    packetStream.write(filename_bytes);           //File name

                }
                //==========End of Packet data===============

                byte[] packet_bytes = packet.toByteArray();

                packet = new ByteArrayOutputStream();
                packetStream = new DataOutputStream(packet);

                packetStream.writeInt(packet_bytes.length); //Packet Length
                packetStream.writeInt(MainActivity.DEVICE_ID); //Source Node ID
                packetStream.writeInt(device_Id);           //Destination Node ID
                packetStream.write(packet_bytes);           //Packet Data

                writeToNode(device_Id, packet.toByteArray());
                debug("Wrote negotiation packet to " + device_Id);
                negotiated = true;      //The negotiation packet is sent to the node
            }catch(IOException e){
                e.printStackTrace();
                return;
            }

        }

        /**
         *
         */
        private void process_metadata(){
            try {

                int fileNameLength = inputStream.readInt(); //File name length
                byte[] fileName_bytes = new byte[fileNameLength];
                inputStream.readFully(fileName_bytes);      //File name
                String fileName = new String(fileName_bytes);

                int checkSum_length = inputStream.readInt();    //checksum length
                byte[] checkSum_bytes = new byte[checkSum_length];
                inputStream.readFully(checkSum_bytes);          //checksum
                String checkSum = new String(checkSum_bytes);

                int sourceNodeID = inputStream.readInt();       //Source node ID
                int destinationNodeID = inputStream.readInt();  //Dest node ID

                long disconnect_time_start = inputStream.readLong();
                long disconnect_time_end = inputStream.readLong();

                long connect_time_start = inputStream.readLong();
                long connect_time_end = inputStream.readLong();

                long scan_time_start = inputStream.readLong();
                long scan_time_end = inputStream.readLong();

//                long connect_delay = inputStream.readLong();    //connection delay
//
//
//                long meeting_delay = inputStream.readLong();    //waiting delay

                Opp_Bundle theBundle = mainActivity.getBundle(fileName);

                if (theBundle != null) {
                    theBundle.setSource(sourceNodeID);
                    theBundle.setDestination(destinationNodeID);
                    theBundle.set_checkSum(checkSum);

                    int numNodes = inputStream.readInt();       //Number of intermediate nodes
                    for (int m = 0; m < numNodes; m++) {
                        int node = inputStream.readInt();       //Intermediate Nodes
                        theBundle.addNode(node);
                    }

                    for (int m = 0; m < (numNodes*2); m++) {
                        long duration = inputStream.readLong(); // disconnect time stamps
                        theBundle.adddisconnectDelay(duration);
                    }

                    for (int m = 0; m < (numNodes*2); m++) {
                        long duration = inputStream.readLong(); // File transfer duration
                        theBundle.addDuration(duration);
                    }
                    for (int m = 0; m < (numNodes*2); m++) {
                        long duration = inputStream.readLong(); //Connection Establishment delay
                        theBundle.addConnectDelay(duration);
                    }
                    for (int m = 0; m < (numNodes*2); m++) {
                        long duration = inputStream.readLong(); //Waiting delay
                        theBundle.addMeetingDelay(duration);
                    }

                    theBundle.addNode(MainActivity.DEVICE_ID);
                    theBundle.addDuration(0);
                    theBundle.addDuration(0);

                    theBundle.adddisconnectDelay(disconnect_time_start);
                    theBundle.adddisconnectDelay(disconnect_time_end);

                    theBundle.addConnectDelay(connect_time_start);
                    theBundle.addConnectDelay(connect_time_end);

                    theBundle.addMeetingDelay(scan_time_start);
                    theBundle.addMeetingDelay(scan_time_end);
                }
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        private void send_meta_data(Opp_Bundle thebundle){
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);

            try {
                //==========Start of Packet data===============
                packetStream.write(OPP_META_DATA);        //Opp neg TAG BYTE
                byte[] filename_bytes = thebundle.getFileName().getBytes();

                packetStream.writeInt(filename_bytes.length); //File name length
                packetStream.write(filename_bytes);           //File name

                byte[] checkSum_bytes = thebundle.getCheckSum().getBytes();
                packetStream.writeInt(checkSum_bytes.length); //checksum length
                packetStream.write(checkSum_bytes);           //checksum

                packetStream.writeInt(thebundle.getSource()); //Source node ID
                packetStream.writeInt(thebundle.getDestination()); //Dest node ID


                packetStream.writeLong(mainActivity.getDisconnect_time_start());
                packetStream.writeLong(mainActivity.getDisconnect_time_end());

                packetStream.writeLong(mainActivity.getConnection_establishment_time_start()); //connection start
                packetStream.writeLong(mainActivity.getConnection_establishment_time_end());
                debug("Connection Establishment " + connectionEstablishment);

                packetStream.writeLong(mainActivity.getScan_time_start());
                packetStream.writeLong(mainActivity.getScan_time_end());

//                packetStream.writeLong(mainActivity.getWaiting_time()); //waiting delay

                ArrayList<Integer> nodes = thebundle.getNodes();
                packetStream.writeInt(nodes.size());    //Number of intermediate nodes

                for (int m = 0; m < nodes.size(); m++) {
                    packetStream.writeInt(nodes.get(m));    //Intermediate Nodes
                }

                ArrayList<Long> disconnectTime = thebundle.getDisconnectTime();
                for (int m = 0; m < disconnectTime.size(); m++) {
                    packetStream.writeLong(disconnectTime.get(m)); //Disconnect time-stamps
                }

                ArrayList<Long> transferTimes = thebundle.getTransferTime();
                for (int m = 0; m < transferTimes.size(); m++) {
                    packetStream.writeLong(transferTimes.get(m)); //File transfer time-stamps
                }

                ArrayList<Long> connection_delay = thebundle.getConnectionEstablishment();
                for (int m = 0; m < connection_delay.size(); m++) {
                    packetStream.writeLong(connection_delay.get(m)); //Connection Establishment time-stamps
                }
                ArrayList<Long> meetingDelay = thebundle.getWaitingDelay();
                for (int m = 0; m < meetingDelay.size(); m++) {
                    packetStream.writeLong(meetingDelay.get(m)); //Scan time-stamps
                }

                byte[] packet_bytes = packet.toByteArray();

                packet = new ByteArrayOutputStream();
                packetStream = new DataOutputStream(packet);

                packetStream.writeInt(packet_bytes.length); //Packet Length
                packetStream.writeInt(MainActivity.DEVICE_ID); //Source Node ID
                packetStream.writeInt(device_Id);           //Destination Node ID
                packetStream.write(packet_bytes);           //Packet Data

                writeToNode(device_Id, packet.toByteArray());
                debug("Wrote Meta-data packet to " + device_Id);
            }catch(IOException e){
                e.printStackTrace();

            }
        }


        /**
         * Read the negotiation packet for opportunistic file transfer
         * This function checks my bundle repository and will ask in the negotiation reply for
         * bundles it doesn't have
         * @param num_bundles       Number of bundles in negotiation packet
         */
        private void process_opportunistic_negotiation(int num_bundles){
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);


    /*
     *                                          Bundle
     * ----------------------------------------------------------------------------------------------
     * | File name length (int) | File name (bytes) | checksum length (int) | checksum (bytes)      |
     * ----------------------------------------------------------------------------------------------
     * | Source node ID (int) | Dest node ID (int) |
     * ----------------------------------------------------------------------------------------------
     * |     disconnect start time-stamp (long)      |       disconnect end time-stamp (long)       |
     * ----------------------------------------------------------------------------------------------
     * | connection establishment start time-stamp (long) | connection establishment end time-stamp (long)
     * ----------------------------------------------------------------------------------------------
     * |        waiting start time-stamp (long)        |       waiting end time-stamp (long)       |
     * ----------------------------------------------------------------------------------------------
     * |                                 Number of intermediate nodes (int)                         |
     * ----------------------------------------------------------------------------------------------
     * |                                 Intermediate Nodes (ints)                                  |
     * ----------------------------------------------------------------------------------------------
     * |                                 File transfer duration (longs)                             |
     * ----------------------------------------------------------------------------------------------
     * |                                 Connection Establishment delay (longs)                     |
     * ----------------------------------------------------------------------------------------------
     * |                                 Waiting delay (longs)                                      |
     * ----------------------------------------------------------------------------------------------
     */

            int bundlesToReceive = 0;
            try {
                for(int i = 0; i < num_bundles; i++){

                    byte isDelivered = inputStream.readByte();
                    int fileNameLength = inputStream.readInt(); //File name length
                    byte[] fileName_bytes = new byte[fileNameLength];
                    inputStream.readFully(fileName_bytes);      //File name
                    String fileName = new String(fileName_bytes);

                    Opp_Bundle theBundle = mainActivity.getBundle(fileName);

                    if(isDelivered == 1){
                        if(theBundle == null){
                            theBundle = new Opp_Bundle( fileName);
                            theBundle.delivered();
                        }
                        else{
                            theBundle.delivered();
                        }

                        if(!mainActivity.oppBundles_queue.contains(theBundle)){
                            mainActivity.oppBundles_queue.add(theBundle);
                        }
                    }
                    else if(isDelivered == 0){
                        if(theBundle == null) {

                            theBundle = new Opp_Bundle(fileName);
                            debug("Will receive\n" + theBundle.toString());

                            packetStream.writeInt(fileNameLength);    //File name length
                            packetStream.write(fileName_bytes);       //file name

                            filesToReceive.add(theBundle);
                            mainActivity.oppBundles_repo.add(theBundle);
                            mainActivity.oppBundles_queue.add(theBundle);

                            bundlesToReceive++;

                        }
                    }

                }

                byte[] packet_bytes = packet.toByteArray();

                packet = new ByteArrayOutputStream();
                packetStream = new DataOutputStream(packet);

                /*                      Negotiation reply packet format
                 * --------------------------------------------------------------------------------
                 * | Packet Length (int)     |       Packet Destination Node ID (int)             |
                 * --------------------------------------------------------------------------------
                 * | Opp neg reply TAG BYTE (byte) | Number of bundles in negotiation reply (int) |
                 * --------------------------------------------------------------------------------
                 * | isOpportunistic |               Bundle ID 1                                  |
                 * --------------------------------------------------------------------------------
                 * | isOpportunistic |                 ......                                     |
                 * --------------------------------------------------------------------------------
                 */
                packetStream.writeInt(1 + 4 + packet_bytes.length);
                packetStream.writeInt(MainActivity.DEVICE_ID);
                packetStream.writeInt(device_Id);

                packetStream.write(OPP_REQUEST);          //Opp neg reply TAG BYTE
                packetStream.writeInt(bundlesToReceive);    //Number of bundles in negotiation reply

                packetStream.write(packet_bytes);           //Needed bundles

                writeToNode(device_Id, packet.toByteArray());



            }
            catch(IOException e){
                debug("ERROR in process_opportunistic_negotiation: "+e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * This function send an ack to the sender notifying him that the bundle with file name
         * "filename" has been successfully received and the sender can proceed by sending the
         * next bundle in his "ToSend" queue
         * @param fileName
         */
        private void send_opp_file_ack(String fileName){
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);
            try {
                 /*                 opportunistic file ACK packet format
                 * ----------------------------------------------------------------------------
                 * |  Packet Length (int)    |       Packet Destination Node ID (int)         |
                 * ----------------------------------------------------------------------------
                 * | Opp file ack TAG BYTE (byte) | File name length (int) | file name (bytes)|
                 * ----------------------------------------------------------------------------
                 */

                packetStream.writeInt(OPP_FILE_ACK);        //Opp file ack TAG BYTE
                byte[] filename_bytes = fileName.getBytes();

                packetStream.writeInt(filename_bytes.length); //File name length
                packetStream.write(filename_bytes);           //file name

                byte[] packet_bytes = packet.toByteArray();

                packet = new ByteArrayOutputStream();
                packetStream = new DataOutputStream(packet);

                packetStream.writeInt(packet_bytes.length); //Packet Length
                packetStream.writeInt(MainActivity.DEVICE_ID);
                packetStream.writeInt(device_Id);           //Packet Destination Node ID

                writeToNode(device_Id, packet.toByteArray());

            }catch(IOException e){
                e.printStackTrace();
            }
        }

        private void process_opportunistic_negotiation_reply(int num_bundles){

            /*                      Negotiation reply packet format
             * --------------------------------------------------------------------------------
             * | Packet Length (int)     |       Packet Destination Node ID (int)             |
             * --------------------------------------------------------------------------------
             * | Opp neg reply TAG BYTE (byte) | Number of bundles in negotiation reply (int) |
             * --------------------------------------------------------------------------------
             * |                                Bundle ID 1                                   |
             * --------------------------------------------------------------------------------
             * |                                   ......                                     |
             * --------------------------------------------------------------------------------
             */

            debug("Processing "+num_bundles+" bundles");
            try {
                for (int i = 0; i < num_bundles; i++) {
//                    int destination = inputStream.readInt();    //Bundle Destination node ID

                    int fileNameLength = inputStream.readInt();//File name length
                    byte[] fileName_bytes = new byte[fileNameLength];
                    inputStream.readFully(fileName_bytes);     //file name
                    String fileName = new String(fileName_bytes);
                    debug("File name:"+fileName);

                    Opp_Bundle theBundle = mainActivity.getBundle(fileName);

                    //Sanity check, I should own that bundle at all times
                    if(theBundle != null) {
                        debug("Will Send "+fileName+" bundle");
                        filesToSend.add(theBundle);
                    }
                    else
                        debug("ERROR: there was a problem with negotiation");
                }

                writeOppFile(); //Start the process of sending bundles
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        /**
         * Function sends advertisement packets about newly connected nodes.
         * The group owner sends the newly connected node's IP address to other group members
         *
         * @param deviceId          Newly introduced node ID
         * @param ipaddress         IP address of newly introduced node
         */
        private void advertiseNewConnection(int deviceId, String ipaddress) {
            for (Map.Entry<Integer, WritingThread> entry : MainActivity.connections.entrySet()) {

                WritingThread conn = entry.getValue();
                if (deviceId != conn.device_Id) {
                    if(conn.WifiDirectConn) {
                        debug("Advertising: " + deviceId + " @ " + ipaddress + " to " + conn.device_Id);
                        DataOutputStream output = conn.outputStream;
                        try {
                            byte[] address_byte = ipaddress.getBytes();
                            ByteArrayOutputStream packet = new ByteArrayOutputStream();
                            DataOutputStream packetStream = new DataOutputStream(packet);

                             /*
                             *                  Advertisement Packet Format
                             * -------------------------------------------------------------------
                             * |   Packet Length (int)  |     Destination Node ID (int)          |
                             * -------------------------------------------------------------------
                             * | ADVERTISEMENT TAG BYTE (byte) | Newly Introduced Node ID (int)  |
                             * -------------------------------------------------------------------
                             * | IP address length (int) |    IP address of new node (bytes)     |
                             * -------------------------------------------------------------------
                             */
                            //==========Start of Packet data===============
                            packetStream.writeInt(1+4+4+address_byte.length); //Packet Length
                            packetStream.writeInt(MainActivity.DEVICE_ID);      //Source Node ID
                            packetStream.writeInt(conn.device_Id);            //Destination Node ID

                            packetStream.writeByte(ADVERTISEMENT);           //ADVERTISEMENT TAG BYTE
                            packetStream.writeInt(deviceId);                //Newly Introduced Node ID
                            packetStream.writeInt(address_byte.length);     //IP address length
                            packetStream.write(address_byte);              //IP address of new node
                            //============End of Packet data================

                            byte[] thePacket = packet.toByteArray();
                            output.write(thePacket, 0, thePacket.length);


                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        /**
         * Terminating connection
         */
        public void terminateConn(){
            debug("Logging bundles");
            mainActivity.log_bundles();
            mainActivity.disconnect_time_start = System.currentTimeMillis();
            debug("Terminating connection");
            readingThread.cancel();
            cancel();
//            mainActivity.clear_networks();
            mainActivity.switch_networks();
        }

        /**
         *  Send an ack for the time sync packet
         * @param sourceID      ID of the node who originated the time sync packet
         * @param fileName      the filename that will be sent to nodeID
         * @param nodeID        the node ID that will receive the file name
         */
        private void ack_timeSync_packet(int sourceID, String fileName, int nodeID){
            int routeID = MainActivity.routePacket(sourceID);
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);

            try {
                byte[] fileName_bytes = fileName.getBytes();
                packetStream.writeInt(1+4+fileName_bytes.length+4);
                packetStream.writeInt(MainActivity.DEVICE_ID);
                packetStream.writeInt(sourceID);
                packetStream.write(RTT_ACK);

                packetStream.writeInt(fileName_bytes.length);
                packetStream.write(fileName_bytes);
                packetStream.writeInt(nodeID);

                writeToNode(routeID, packet.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Function will check if we time sync packet is enabled and will work accordingly
         * @param nodeID
         * @param fileName
         */
        public void sendFile(int nodeID, String fileName){

            //Check if time sync packet is enabled, send the time sync packet
            if(MainActivity.time_sync){
                int routeID = MainActivity.routePacket(nodeID);
                ByteArrayOutputStream packet = new ByteArrayOutputStream();
                DataOutputStream packetStream = new DataOutputStream(packet);

                try {
                    byte[] fileName_bytes = fileName.getBytes();

                    packetStream.writeInt(1+4+4+fileName_bytes.length+4);
                    packetStream.writeInt(MainActivity.DEVICE_ID);
                    packetStream.writeInt(routeID);
                    packetStream.write(RTT);
                    packetStream.writeInt(MainActivity.DEVICE_ID);
                    packetStream.writeInt(fileName_bytes.length);
                    packetStream.write(fileName_bytes);
                    packetStream.writeInt(nodeID);

                    t_initial_start = System.currentTimeMillis();
                    writeToNode(routeID, packet.toByteArray());

                    connectionEstablishment = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //if time sync packet is not enabled send the file directly
            else{
                writeFile(nodeID, fileName);
            }
        }

        public void writeFile(int nodeID, String fileName) {
            try {
                FileInputStream inputStream = new FileInputStream((MainActivity.rootDir + fileName));
                int FileSize = inputStream.available();
                int chunkSize = 8192, len; //8 KB

                byte[] buf = new byte[chunkSize];
                int packetsNum = 0;
                log("Sending file "+fileName+" to "+nodeID);
                int destID = MainActivity.routePacket(nodeID);

                while ((len = inputStream.read(buf)) > 0) {


                    ByteArrayOutputStream packet = new ByteArrayOutputStream();
                    DataOutputStream packetStream = new DataOutputStream(packet);

                    packetStream.write(DATA);
                    packetStream.write(0);
                    byte[] fileName_bytes = fileName.getBytes();
                    packetStream.writeInt(fileName_bytes.length);
                    packetStream.write(fileName_bytes);
                    packetStream.writeInt(FileSize);

                    packetStream.write(buf, 0, len);

                    byte[] packet_bytes = packet.toByteArray();


                    packet = new ByteArrayOutputStream();
                    packetStream = new DataOutputStream(packet);

                    packetStream.writeInt(packet_bytes.length);
                    packetStream.writeInt(MainActivity.DEVICE_ID);
                    packetStream.writeInt(nodeID);

                    packetStream.write(packet_bytes, 0, packet_bytes.length);

                    debug("Packet length " + packet_bytes.length + " to " + nodeID);
                    packetsNum++;
                    writeToNode(destID, packet.toByteArray());
                }
                debug("Packets Num: "+packetsNum);
                send_terminating_packet(destID);
                log("Sent file "+fileName+" to "+nodeID);

                debug("Done writing file "+fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         *
         */
        public void writeOppFile(){
            if(!filesToSend.isEmpty()) {

                try {
                    Opp_Bundle thebundle = filesToSend.peek();
                    send_meta_data(thebundle);

                    String fileName = thebundle.getFileName();
                    FileInputStream inputStream = new FileInputStream((MainActivity.rootDir + fileName));
                    int FileSize = inputStream.available();
                    int chunkSize = 10240, len; //10 KB

                    /*
                     *                     DATA Packet Format
                     * -----------------------------------------------------------------------------
                     * | Packet Length (int) | Source Node ID (int) |  Destination Node ID (int)   |
                     * -----------------------------------------------------------------------------
                     * | Data TAG BYTE (byte) | is opportunistic (byte)  | file name length (int)  |
                     * -----------------------------------------------------------------------------
                     * | file name (byte)   |      File Size (int)   |       DATA of file          |
                     * -----------------------------------------------------------------------------
                     */

                    byte[] buf = new byte[chunkSize];
                    debug("[OPPORTUNISTIC] Sending file "+fileName+" to "+device_Id);

                    while((len = inputStream.read(buf)) > 0){

                        ByteArrayOutputStream packet = new ByteArrayOutputStream();
                        DataOutputStream packetStream = new DataOutputStream(packet);

                        packetStream.write(DATA); //Data TAG BYTE
                        packetStream.write(1);    //is opportunistic
                        byte[] fileName_bytes = fileName.getBytes();
                        packetStream.writeInt(fileName_bytes.length); //file name length
                        packetStream.write(fileName_bytes);           //file name in bytes
                        packetStream.writeInt(FileSize);              //File Size

                        packetStream.write(buf, 0, len);              //DATA of file

                        byte[] packet_bytes = packet.toByteArray();

                        packet = new ByteArrayOutputStream();
                        packetStream = new DataOutputStream(packet);

                        packetStream.writeInt(packet_bytes.length); //Packet Length
                        packetStream.writeInt(MainActivity.DEVICE_ID);
                        packetStream.writeInt(device_Id);           //Destination Node ID

                        packetStream.write(packet_bytes, 0, packet_bytes.length);

                        writeToNode(device_Id, packet.toByteArray());
                    }

                    debug("Done writing "+fileName);

                }
                catch (IOException e) {
                    debug("Error in writeOppFile: "+e.getMessage());
                }
            }
        }


        public void send_terminating_packet(int nodeID){
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream packetStream = new DataOutputStream(packet);
            try {
                packetStream.writeInt(1);
                packetStream.writeInt(MainActivity.DEVICE_ID);
                packetStream.writeInt(nodeID);
                packetStream.write(4);

                writeToNode(nodeID, packet.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public synchronized void writeToNode(int nodeID, byte[] packet) {
            WritingThread thread = MainActivity.connections.get(nodeID);
            if(thread != null)
                try {
                    thread.outputStream.write(packet, 0, packet.length);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        public void cancel(){
            engineOn = false;
            try {
                outputStream.close();
                debug("Removing "+device_Id+" from the pool of connections");
                MainActivity.connections.remove(device_Id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
