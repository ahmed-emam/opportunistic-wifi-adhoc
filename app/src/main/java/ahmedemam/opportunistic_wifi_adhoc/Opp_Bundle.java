package ahmedemam.opportunistic_wifi_adhoc;

import java.util.ArrayList;

/**
 * Created by Ahmed Emam on 5/18/15.
 */

/**
 * A class that represent a bundle exchanged between nodes
 */
public class Opp_Bundle {

    private int source = 0;         //Node that produced the bundle


    private int destination = 0;    //Node that should receive the bundle
    private String fileName;        //Data of the bundle
    private ArrayList<Integer> nodes;  //Intermediate nodes that this bundle went through



    private ArrayList<Long> disconnectTime;
    private ArrayList<Long> transferTime;  //Data transfer duration at each intermediate node
    private ArrayList<Long> connectionEstablishment;    //Connection establishment delay at each
    //intermediate nodes
    private ArrayList<Long> waitingDelay;   //Delay that represent
    private long start_recv_time;   //TimeStamp of when I received the first data packet of the file
    private long end_recv_time;     //TimeStamp of when I received the last data packet of the file
    private String checkSum;        //CheckSum of the file to check for successful data transfer
    private boolean isDelivered;

    public String getFileName(){
        return fileName;
    }
    public int getSource() {
        return source;
    }
    public int getDestination() {
        return destination;
    }
    public ArrayList<Integer> getNodes() {
        return nodes;
    }
    public ArrayList<Long> getConnectionEstablishment() {
        return connectionEstablishment;
    }
    public ArrayList<Long> getDisconnectTime() {
        return disconnectTime;
    }
    public String getCheckSum() {
        return checkSum;
    }
    public ArrayList<Long> getWaitingDelay() {
        return waitingDelay;
    }
    public ArrayList<Long> getTransferTime() {
        return transferTime;
    }
    public long getStart_recv_time() {
        return start_recv_time;
    }
    public long getEnd_recv_time() {
        return end_recv_time;
    }

    /**
     *
     * @param sourceNodeID
     * @param destinationNodeID
     * @param filename
     */
    public Opp_Bundle(int sourceNodeID, int destinationNodeID, String filename){
        source = sourceNodeID;
        destination = destinationNodeID;
        fileName = filename;
        nodes = new ArrayList<>();
        transferTime = new ArrayList<>();
        connectionEstablishment = new ArrayList<>();
        waitingDelay = new ArrayList<>();
        disconnectTime = new ArrayList<>();
        start_recv_time = -1;
        end_recv_time = -1;
        checkSum = "";
        isDelivered = false;
    }

    public Opp_Bundle(String filename){
        fileName = filename;
        source = -1;
        destination = -1;
        nodes = new ArrayList<>();
        transferTime = new ArrayList<>();
        connectionEstablishment = new ArrayList<>();
        waitingDelay = new ArrayList<>();
        disconnectTime = new ArrayList<>();
        start_recv_time = -1;
        end_recv_time = -1;
        checkSum = "";
        isDelivered = false;
    }

    public void adddisconnectDelay(long disconnect) { disconnectTime.add(disconnect);}
    public void addNode(int nodeID){
        nodes.add(nodeID);
    }
    public void addDuration(long duration){
        transferTime.add(duration);
    }
    public void addConnectDelay(long duration){
        connectionEstablishment.add(duration);
    }
    public void addMeetingDelay(long duration){ waitingDelay.add(duration); }
    public void set_end_recv_time(long recv_time){
        this.end_recv_time = recv_time;
    }
    public void set_start_recv_time(long recv_time){
        this.start_recv_time = recv_time;
    }
    public void set_checkSum(String checkSum){
        this.checkSum = checkSum;
    }

    public void setDestination(int destination) {
        this.destination = destination;
    }

    public void setSource(int source) {
        this.source = source;
    }
    public void setDuration(long start, long end){
        for(int i = 0; i < nodes.size(); i++){
            if(nodes.get(i) == MainActivity.DEVICE_ID){
                /**
                 * Node 1 transfer time stamps are at index 2 and 3
                 *
                 */
                int index = i*2;
                transferTime.set(index, start);
                transferTime.set(index+1, end);
//                long oldTime = transferTime.get(i);
//                MainActivity.debug("Old duration "+oldTime);
//                transferTime.set(i, (oldTime + duration));
//                MainActivity.debug("New duration "+transferTime.get(i));
            }
        }
    }

    public void delivered(){
        isDelivered = true;
    }

    public boolean isDelivered(){
        return isDelivered;
    }

    public String toString(){
        String bundle = "======Opp_Bundle======";
        bundle += "\nDelivered:"+ (this.isDelivered? "Yes":"No");
        bundle += "\nSource:"+source;
        bundle += "\nDestination:"+destination;
        bundle += "\nFile name: "+fileName;
        bundle += "\nNodes:\t";
        for(int i = 0; i < nodes.size(); i++)
            bundle += nodes.get(i)+"\t";


        bundle += "\nDisconnect Time:\t";
        for(int i = 0; i < disconnectTime.size(); i++)
            bundle += disconnectTime.get(i)+"\t";

        bundle += "\nTransfer Time:\t";
        for(int i = 0; i < transferTime.size(); i++)
            bundle += transferTime.get(i)+"\t";

        bundle += "\nConnect Delay:\t";
        for(int i = 0; i < connectionEstablishment.size(); i++)
            bundle += connectionEstablishment.get(i)+"\t";

        bundle += "\nScan Delay:\t";
        for(int i = 0; i < waitingDelay.size(); i++)
            bundle += waitingDelay.get(i)+"\t";

        return bundle;
    }

    public boolean isEqual(Opp_Bundle other){
        if(this.source != other.source)
            return false;
        if(this.destination != other.destination)
            return false;
        return this.fileName.equals(other.fileName);

    }


}
