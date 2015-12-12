package ahmedemam.opportunistic_wifi_adhoc;

import android.bluetooth.BluetoothAdapter;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;


/**
 *
 * Implementation of opportunistic DTN for wifi adhoc
 * 6 --> 7 --> 8 --> 9
 * device 6 will send a file to device 9
 *
 */

public class MainActivity extends ActionBarActivity {
    private static String file_name = "20M_20141111_140119.txt";
    public static final String TAG = "Wifiadhoc";
    private static final String uuid = "e110cf10-7866-11e4-82f8-0800200c9a66";
    public static int DEVICE_ID = 0;
    public static boolean isGroupOwner = false;
    public static String rootDir = Environment.getExternalStorageDirectory().toString() + "/MobiBots/wifiadhoc-opportunistic/";
    public static HashMap<Integer, ConnectionThread.WritingThread> connections;
    public static boolean time_sync = true;
    private static FileOutputStream logfile;
    private static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static String BTTAG = "Bluetooth_test";
    private static boolean D = true;
    private static HashMap<Integer, Integer> routingTable;
    private static Set<Integer> nearby_devices;


    public ArrayList<Opp_Bundle> oppBundles_repo;
    public Queue<Opp_Bundle> oppBundles_queue;

    /*******************************************
     *   START List of time-stamped timers
     * *****************************************
     */
    public long connection_establishment_time_start = 0L;
    public long connection_establishment_time_end = 0L;
    public long disconnect_time_start = 0L;
    public long disconnect_time_end = 0L;
    public long disconnect_time = 0L;
    public long scan_time_start = 0L;
    public long scan_time_end = 0L;
    public long waiting_time = 0L;

    public long getDisconnect_time_start() {
        return disconnect_time_start;
    }
    public long getDisconnect_time_end() {
        return disconnect_time_end;
    }
    public long getScan_time_start() {
        return scan_time_start;
    }
    public long getScan_time_end() {
        return scan_time_end;
    }
    public long getConnection_establishment_time_start() {
        return connection_establishment_time_start;
    }
    public long getConnection_establishment_time_end() {
        return connection_establishment_time_end;
    }

    public long getWaiting_time() {
        return waiting_time;
    }
    /*******************************************
     *   END List of time-stamped timers
     * *****************************************
     */


    public String[] device_Wifi_adresses = {
            "D8:50:E6:83:D0:2A",
            "D8:50:E6:83:68:D0",
            "D8:50:E6:80:51:09",
            "24:DB:ED:03:47:C2",
            "24:DB:ED:03:49:5C",
            "8c:3a:e3:6c:a2:9f",
            "8c:3a:e3:5d:1c:ec",
            "c4:43:8f:f6:3f:cd",
            "f8:a9:d0:02:0d:2a"
    };
    public String[] device_BT_adresses = {
            "D8:50:E6:83:D0:2A",
            "D8:50:E6:83:68:CF",
            "D8:50:E6:80:51:08",
            "24:DB:ED:03:47:C1",
            "24:DB:ED:03:49:5B",
            "BC:F5:AC:85:56:1A",
            "BC:F5:AC:7D:29:EE",
            "8c:3a:e3:fd:6f:b2",
            "cc:fa:00:7a:d6:68"
    };

    public String[] device_ip_adresses = {
            "",
            "",
            "",
            "",
            "",
            "10.0.0.1",
            "10.0.0.2",
            "10.0.0.3",
            "10.0.0.4",
            "",
            ""
    };


    public MainActivity theMainActivity;
    IntentFilter peerfilter;
    IntentFilter connectionfilter;
    public Handler scheduler;

    private boolean experiment_started = false;
    public boolean connected_to_network = false;
    public DatagramSocket broadcastSocket = null;


//    /**
//     *Any changes to the wifi direct group are broadcasted to this receiver
//     */
//    BroadcastReceiver connectionChangedReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
//            final String address = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
//            Log.d(TAG, "My IP Address: " + address);
//
//            WifiP2pGroup group = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
//            if (group != null) {
//                Log.i(TAG, "SSID: " + group.getNetworkName());
//                Log.i(TAG, "Passphrase: " + group.getPassphrase());
//                Log.i(TAG, "Interface: " + group.getInterface());
//                Log.i(TAG, "Group Owner address " + ((group.getOwner() != null) ? group.getOwner().deviceAddress : "null"));
//                for (WifiP2pDevice device : group.getClientList()) {
//                    Log.i(TAG, device.deviceAddress);
//                }
//            }
//
//            // Extract the NetworkInfo
//            String extraKey = WifiP2pManager.EXTRA_NETWORK_INFO;
//            NetworkInfo networkInfo = intent.getParcelableExtra(extraKey);
//            Log.i(TAG, networkInfo.getState().name());
//
//            // Check if we're connected
//            if (networkInfo.isConnected()) {
//
//                wifiP2pManager.requestConnectionInfo(wifiDirectChannel,
//                        new WifiP2pManager.ConnectionInfoListener() {
//                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
//                                // If the connection is established
//                                if (info.groupFormed) {
//                                    Log.i(TAG, info.toString());
//                                    // If we're the server
//                                    if (info.isGroupOwner) {
//                                        isGroupOwner = true;
//                                        // TODO Initiate server socket.
//                                        Log.d(TAG, "I am the group owner");
//
//                                        Log.d(TAG, "Group owner IP: " + info.groupOwnerAddress.getHostAddress());
//
//                                        if (wifiDirectServerThread == null) {
//                                            wifiDirectServerThread = new WifiServerThread(theMainActivity);
//                                            wifiDirectServerThread.start();
//                                        }
//                                    }
//                                }
//                            }
//                        });
//            } else {
//                Log.d(TAG, "Wi-Fi Direct Disconnected");
//                debug("disconnect counter ended");
//                disconnect_time = System.currentTimeMillis() - disconnect_time_start;
//                debug("Disconnected as a Group owner and waiting for new connections" +
//                        " and it took " + disconnect_time);
//            }
//        }
//    };



//    private WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
//        public void onFailure(int reason) {
//            String errorMessage = "WiFi Direct Failed: ";
//            switch (reason) {
//                case WifiP2pManager.BUSY:
//                    errorMessage += "Framework busy.";
//                    break;
//                case WifiP2pManager.ERROR:
//                    errorMessage += "Internal error.";
//                    break;
//                case WifiP2pManager.P2P_UNSUPPORTED:
//                    errorMessage += "Unsupported.";
//                    break;
//                default:
//                    errorMessage += "Unknown error.";
//                    break;
//            }
//            Log.d(TAG, errorMessage);
//        }
//
//        public void onSuccess() {
//            // Success!
//            // Return values will be returned using a Broadcast Intent
//        }
//    };

//    /**
//     * This is a broadcast receiver to receive any changes to the wifi connection
//     */
//    private BroadcastReceiver myWifiReceiver = new BroadcastReceiver() {
//
//        @Override
//        public void onReceive(Context arg0, Intent arg1) {
//            // TODO Auto-generated method stub
//            NetworkInfo networkInfo = arg1.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
//            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
//
//                ConnectivityManager myConnManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
//                NetworkInfo myNetworkInfo = myConnManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
//                WifiManager myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//                WifiInfo myWifiInfo = myWifiManager.getConnectionInfo();
//
//                NetworkInfo activeNetworkInfo = myConnManager.getActiveNetworkInfo();
//                if(activeNetworkInfo != null) {
//                    debug("===============This is the active network===============");
//                    debug("State: " + activeNetworkInfo.getDetailedState().name());
//                    debug("Name: "+ myWifiInfo.getSSID());
//
//                    debug("=========================================================");
//                }
//
//                debug(myWifiInfo.getMacAddress());
//
//                if (myNetworkInfo.isConnected()) {
//                    debug("--- CONNECTED ---");
//                    int myIp = myWifiInfo.getIpAddress();
//                    final String address = Formatter.formatIpAddress(myIp);
//                    Log.d(TAG, "My IP Address: " + address);
//                    debug("SSID: " + myWifiInfo.getSSID());
//                    debug("BSSID: " + myWifiInfo.getBSSID());
//
//                    String networkSSID;
//
//                    //Once you are connected to the specified wifi direct group (Wifi network)
//                    if (myWifiInfo.getSSID().equals("\"" + CurrentNetworkSSID + "\"")) {
//
//                        Scanner scanner = new Scanner(CurrentNetworkSSID);
//                        scanner.next();
//                        int nodeID = Integer.parseInt(scanner.next());
//                        connected_to_network = true;
//
//                        //If i am not previously connected to the group owner of this network
//                        if (connections.get(nodeID) == null) {
//                            if (wifiDirectServerThread == null) {
//                                wifiDirectServerThread = new WifiServerThread(theMainActivity);
//                                wifiDirectServerThread.start();
//                            }
//
//                            wifiDirectClientThread = new WifiClientThread(theMainActivity, nodeID,
//                                    Formatter.formatIpAddress(myWifiManager.getDhcpInfo().serverAddress));
//                            wifiDirectClientThread.start();
//                        }
//                    }
//                }
//
//
//
//            }
//        }
//    };

    /**
     * According to the routing table,
     * who should I forward messages to for it to reach nodeID
     * @param nodeID        device's ID
     * @return
     */
    public static synchronized int routePacket(int nodeID) {
        Integer sendToNode = routingTable.get(nodeID);
        if (sendToNode != null) {
            debug("Packet to " + nodeID + " send it to " + sendToNode);
            return sendToNode;
        } else
            return -1;
    }

    /**
     * Print out debug messages if "D" (debug mode) is enabled
     * @param message
     */
    public static void debug(String message) {
        if (D) {
            Log.d(TAG, message);
        }
    }


    public static synchronized String getTimeStamp() {
        return (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(new Date()));
    }

    /**
     * Function that write 'message' to the log file
     * @param message
     */
    public static synchronized void log(String message) {
        StringBuilder log_message = new StringBuilder(26 + message.length());
        log_message.append(getTimeStamp());
        log_message.append(": ");
        log_message.append(message);
        log_message.append("\n");

        try {
            logfile.write(log_message.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    public synchronized void addRoute(int destNode, int routeNode){
        debug("Route to "+destNode+" through "+routeNode);
        routingTable.put(destNode, routeNode);
    }


    /**
     * Return the bundle with file name "fileName" and destination ID "destination"
     * @param fileName
     * @param destination
     * @return
     */
    public Opp_Bundle getBundle(String fileName, int destination){
        for(int i = 0; i < oppBundles_repo.size(); i++){
            Opp_Bundle theOppBundle = oppBundles_repo.get(i);
            String file_name = theOppBundle.getFileName();

            int destID = theOppBundle.getDestination();
            if(file_name.equals(fileName) && (destination == destID)){
                return theOppBundle;
            }
        }
        return null;
    }

    /**
     * Return the bundle with file name "fileName"
     * @param fileName
     * @return
     */
    public Opp_Bundle getBundle(String fileName){
        for(int i = 0; i < oppBundles_repo.size(); i++){
            Opp_Bundle theOppBundle = oppBundles_repo.get(i);
            String file_name = theOppBundle.getFileName();

            if(file_name.equals(fileName)){
                return theOppBundle;
            }
        }
        return null;
    }




    /**
     * Return device's ID mapping according to the device's wifi mac address
     * @param deviceAddress     Bluetooth Address
     * @return                  device ID
     */
    public int findDevice_Wifi(String deviceAddress) {
        for (int i = 0; i < device_Wifi_adresses.length; i++) {
            if (device_Wifi_adresses[i].equalsIgnoreCase(deviceAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }
    /**
     * Return device's ID mapping according to the device's bluetooth address
     * @param deviceAddress     Bluetooth Address
     * @return                  device ID
     */
    public int findDevice_BT(String deviceAddress) {
        for (int i = 0; i < device_BT_adresses.length; i++) {
            if (device_BT_adresses[i].equalsIgnoreCase(deviceAddress)) {
                return (i + 1);
            }
        }
        return -1;
    }


    private String convertByteArrayToHexString(byte[] arrayBytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return stringBuffer.toString();
    }

    /**
     * Function to create a hash out of content of a file
     * @param fileName
     * @param algorithm
     * @return
     */
    public String hashFile(String fileName, String algorithm) {
        try  {
            FileInputStream inputStream = new FileInputStream(new File((MainActivity.rootDir + fileName)));
            MessageDigest digest = MessageDigest.getInstance(algorithm);

            byte[] bytesBuffer = new byte[1024];
            int bytesRead = -1;

            while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
                digest.update(bytesBuffer, 0, bytesRead);
            }
            byte[] hashedBytes = digest.digest();

            return convertByteArrayToHexString(hashedBytes);
        } catch (NoSuchAlgorithmException | IOException ex) {
            return "error: "+ex.getMessage();
        }
    }

//    /**
//     * Receiver for wifi scan results.
//     */
//    private BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//
//            if (experiment_started) {
//                if (!connected_to_network) {
//                    WifiManager myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//                    List<ScanResult> mScanResults = myWifiManager.getScanResults();
//                    for (ScanResult scan : mScanResults) {
//
//                        //if the network SSID contains DIRECT meaning that it is a wifi direct
//                        //group that is beaconing its SSID
//                        if (scan.SSID.contains("DIRECT")) {
//
//                            //if i am currently not connecting or connected to a network
//                            if (CurrentNetworkSSID.equals("")) {
//
//                                Scanner scanner = new Scanner(scan.SSID);
//                                scanner.next();
//                                String nodeID = scanner.next();
//                                debug("The node Id " + nodeID);
//
//                                //Check if there bundles needed to be sent to that device
//                                if (shouldConnect(Integer.parseInt(nodeID))) {
//                                    debug("Connecting to " + scan.SSID);
//                                    CurrentNetworkSSID = scan.SSID;
//                                    int i;
//                                    for (i = 0; i < SSID.length; i++)
//                                        if (SSID[i].equals(CurrentNetworkSSID))
//                                            break;
//                                    scan_time_end = System.currentTimeMillis();
//                                    waiting_time = scan_time_end - scan_time_start;
//                                    debug("waiting counter ended");
//                                    connectToWifi(SSID[i], passphrases[i]);
//                                    return;
//                                }
//                            }
//                            else{
//                                //if the network that I am supposed to connect to doesn't show up
//                                //in the wifi scan result then connect to another network
//                                //If the network doesn't show up in the wifi scan result then it is
//                                //not available
//                                if(!scan.SSID.equals(CurrentNetworkSSID)){
//                                    CurrentNetworkSSID = "";
//                                }
//                            }
//                        }
//                    }
//                    //Scan wifi Access points after 5 seconds
//                    scheduler.postDelayed(scanWifiNearbyDevices, 5000);
//                }
//            }
//        }
//    };

    /**
     * This function goes through the bundles and checks if we there are any bundles that should be
     * sent to the device with "nodeID"
     * @param nodeID
     * @return
     */
    public boolean shouldConnect(int nodeID){
        for (Opp_Bundle theBundle : oppBundles_queue) {

            if (theBundle.isDelivered()) {
                return true;
            }
            else {
                if (DEVICE_ID == theBundle.getDestination())
                    continue;

                if (nodeID == theBundle.getSource()) {
                    continue;
                }
                boolean dont_choose = false;
                ArrayList<Integer> nodes = theBundle.getNodes();
                for (int m = 0; m < nodes.size(); m++) {
                    if (nodes.get(m) == nodeID) {
                        dont_choose = true;
                    }
                }
                if (dont_choose)
                    continue;


                return true;
            }
        }
//        for(int i = 0; i < oppBundles_queue.size(); i++){
//            Opp_Bundle theBundle = oppBundles_repo.get(i);
//            if(DEVICE_ID == theBundle.getDestination())
//                continue;
//
//            if(nodeID == theBundle.getSource()){
//                continue;
//            }
//            boolean dont_choose = false;
//            ArrayList<Integer> nodes = theBundle.getNodes();
//            for(int m = 0; m < nodes.size(); m++){
//                if(nodes.get(m) == nodeID){
//                    dont_choose = true;
//                }
//            }
//            if(dont_choose)
//                continue;
//
//            return true;
//        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        theMainActivity = this;


        WifiManager wifiMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        DEVICE_ID = findDevice_Wifi(wifiInfo.getMacAddress());

        peerfilter = new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        connectionfilter = new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

//        registerReceiver(myWifiReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        connections = new HashMap<>();
        routingTable = new HashMap<>();
        nearby_devices = new HashSet<>();

        oppBundles_repo = new ArrayList<>();
        oppBundles_queue = new LinkedList<>();

        scheduler = new Handler();

        initLogging();

    }

    public void check_nearby_devices(){
        scheduler.removeCallbacks(scanWifiNearbyDevices);
        for(Integer deviceID : nearby_devices){
            int deviceid = deviceID.intValue();
            if(shouldConnect(deviceID)){
                if(deviceid == (DEVICE_ID+1)){
                    //TODO: Connect to device
                    scan_time_end = System.currentTimeMillis();
                    connection_establishment_time_start = System.currentTimeMillis();
                    WifiClientThread wifiClientThread = new WifiClientThread(theMainActivity, deviceid,
                            device_ip_adresses[deviceid-1]);
                    wifiClientThread.start();



                    return;


                }
            }
        }
        scheduler.postDelayed(scanWifiNearbyDevices, 3000);

    }

    //A runnable that does wifi network scan every X seconds
    private Runnable scanWifiNearbyDevices = new Runnable() {
        @Override
        public void run() {
            debug("Scanning");
            check_nearby_devices();
        }
    };

    private Runnable checkBundles = new Runnable() {
        @Override
        public void run() {

            if(oppBundles_queue.isEmpty())
            {
                scheduler.postDelayed(checkBundles, 10000);
            }
            else{
                debug("waiting counter started");
                scan_time_start = System.currentTimeMillis();
                debug("We have something to say");
                scheduler.removeCallbacks(checkBundles);
                scheduler.postDelayed(scanWifiNearbyDevices, 0);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Function to log all the bundles available at the current node
     */
    public void log_bundles(){
        log("===============Logging Bundles===============");
        debug("===============Logging Bundles===============");
        for(int i = 0; i < oppBundles_repo.size(); i++) {

            Opp_Bundle theBundle = oppBundles_repo.get(i);
            log("File Name:"+theBundle.getFileName()+"\tDestination:"+theBundle.getDestination()
                    +"\tSource:"+theBundle.getSource());
            debug("File Name:"+theBundle.getFileName()+"\tDestination:"+theBundle.getDestination()
                    +"\tSource:"+theBundle.getSource());

            ArrayList<Integer> nodes = theBundle.getNodes();
            String nodes_in_String = "Nodes:\t\t\t";
            for(int m = 0; m < nodes.size(); m++){
                nodes_in_String += (nodes.get(m)+"\t"+nodes.get(m)+"\t");
            }
            log(nodes_in_String);
            debug(nodes_in_String);


            ArrayList<Long> disconnectTime = theBundle.getDisconnectTime();
            String disconnectTimes_in_String = "Disconnect_Time:\t";
            for(int m = 0; m < disconnectTime.size(); m++){
                disconnectTimes_in_String += (disconnectTime.get(m)+"\t");
            }
            log(disconnectTimes_in_String);
            debug(disconnectTimes_in_String);


            ArrayList<Long> meeting_delay = theBundle.getWaitingDelay();
            String meetingDelay_in_String = "Scan_Time:\t\t";
            for(int m = 0; m < meeting_delay.size(); m++){
                meetingDelay_in_String += (meeting_delay.get(m) + "\t");
            }
            log(meetingDelay_in_String);
            debug(meetingDelay_in_String);


            ArrayList<Long> connect_delay = theBundle.getConnectionEstablishment();
            String connectDelay_in_String = "Connect_Time:\t\t";
            for(int m = 0; m < connect_delay.size(); m++){
                connectDelay_in_String += (connect_delay.get(m)+"\t");
            }
            log(connectDelay_in_String);
            debug(connectDelay_in_String);

            ArrayList<Long> transferTimes = theBundle.getTransferTime();
            String transferTimes_in_String = "Transfer_Time:\t\t";
            for(int m = 0; m < transferTimes.size(); m++){
                transferTimes_in_String += (transferTimes.get(m)+"\t");
            }
            log(transferTimes_in_String);
            debug(transferTimes_in_String);



            log("CheckSum: "+theBundle.getCheckSum());

        }
        log("===============Done Logging Bundles===============");
        debug("===============Done Logging Bundles===============");
    }



    private void initLogging() {
        File mediaStorageDir = new File(rootDir);
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss").format(new Date());
        File file = new File(rootDir + "Device_" +DEVICE_ID + "_" +timeStamp + ".txt");
        Log.d(TAG, file.getPath());
        try {

            logfile = new FileOutputStream(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * start an experiment
     * @param v
     */
    public void start_experiment(View v){

        String filename = file_name;
        Opp_Bundle newBundle = new Opp_Bundle(6,9, filename);

        newBundle.set_checkSum(hashFile(filename, "MD5"));

        oppBundles_repo.add(newBundle);
        oppBundles_queue.add(newBundle);



        switch_networks();

        disconnect_time_end = 0;
        disconnect_time_start = 0;
    }

    /**
     *
     */
    public void switch_networks(){
        experiment_started = true;
        disconnect_time_end = System.currentTimeMillis();
        scheduler.postDelayed(checkBundles, 0);
    }

    /**
     * Send file to device with device ID "device"
     * @param v
     */
    public void send_file_to_device(View v) {
        EditText ipAddressEdit = (EditText) findViewById(R.id.device_ID);
        String ipAddress = ipAddressEdit.getText().toString();
        int device = Integer.valueOf(ipAddress);
        int node = routePacket(device);
        ConnectionThread.WritingThread thread = connections.get(node);
        thread.sendFile(device, file_name);
    }

    /**
     * Start a new experiment
     * This is triggered by a button
     * @param v
     */
    public void newExperiment(View v){
        experiment_started = false;
        debug("===========Starting a NEW EXPERIMENT===========");
        log("===================NEW EXPERIMENT===================");

//        clear_networks();

        WifiServerThread serverThread = new WifiServerThread(theMainActivity);
        serverThread.start();

        WifiBroadcast_server broadcast_server = new WifiBroadcast_server();
        broadcast_server.start();

        WifiBroadcast_client broadcast_client = new WifiBroadcast_client();
        broadcast_client.start();

        File file = new File(rootDir + file_name);
        Opp_Bundle theBundle = getBundle(file_name);
        if(theBundle != null){
            oppBundles_repo.remove(theBundle);
        }
        if(DEVICE_ID != 6) {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
//        unregisterReceiver(peerDiscoveryReceiver);
//        unregisterReceiver(connectionChangedReceiver);
//        unregisterReceiver(wifiStateReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        debug("Stopping the app");
//        clear_networks();
        log_bundles();
        scheduler.removeCallbacks(scanWifiNearbyDevices);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        registerReceiver(connectionChangedReceiver, connectionfilter);
//        registerReceiver(wifiStateReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    /**
     * Thread responsible of starting a listening wifi socket and accepting new coming connection
     */
    public static class WifiServerThread extends Thread {
        ServerSocket serverSocket = null;
        MainActivity mainActivity;
        boolean serverOn = true;
        public WifiServerThread(MainActivity activity) {
            mainActivity = activity;
            try {
                serverSocket = new ServerSocket(8000);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void run() {
            while (serverOn) {
                try {
                    Log.d(TAG, "Server Thread");
                    Log.i(TAG, "Started: " + serverSocket.getLocalSocketAddress());
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "Connected to: " + client.getInetAddress().getHostAddress());
                    Log.d(TAG, "Connected to Local Address: " + client.getLocalAddress().getHostAddress());

                    ConnectionThread thread = new ConnectionThread(mainActivity,client, false, true);
                    thread.start();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
        public void cancel(){
            try {
                serverOn = false;
                serverSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Thread responsible of connecting to a specific host address and acquiring a communication
     * socket with that host
     */
    public static class WifiClientThread extends Thread {
        String hostAddress;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        int device_Id;

        MainActivity mainActivity;
        public WifiClientThread(MainActivity activity,
                                int deviceId, String host) {
            mainActivity = activity;
            hostAddress = host;
            device_Id = deviceId;
        }

        @Override
        public void run() {
            /**
             * Listing 16-26: Creating a client Socket
             */
            int timeout = 500;
            int port = 8000;

            Socket socket = new Socket();
            try {
                debug("Connecting to " + device_Id + " @ " + hostAddress);
                socket.bind(null);
                socket.connect((new InetSocketAddress(hostAddress, port)), 5000);


                ConnectionThread conn = new ConnectionThread(mainActivity,
                        socket, true, true);
                conn.device_Id = device_Id;

                conn.start();


            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
            // TODO Start Receiving Messages
        }
    }



    private class WifiBroadcast_server extends Thread{
        int port = 5555;
        public WifiBroadcast_server(){
            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            debug("Started advertising presence");
            while(true){
                try {
                    String messageStr = "Device " + DEVICE_ID;
                    byte[] messageByte = messageStr.getBytes();

                    InetAddress group = InetAddress.getByName("10.0.0.255");
                    DatagramPacket packet = new DatagramPacket(messageByte, messageByte.length, group, port);
                    debug("Broadcasting "+messageStr);
                    broadcastSocket.send(packet);

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }


    private class WifiBroadcast_client extends Thread{
        int port = 5555;
        public WifiBroadcast_client(){

            if(broadcastSocket == null)
                try {
                    broadcastSocket = new DatagramSocket(port);
                    broadcastSocket.setBroadcast(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
        }

        @Override
        public void run() {
            while(true){
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    broadcastSocket.receive(packet);
                    byte[]data = packet.getData();

                    String messageStr = new String(data, 0,packet.getLength());


                    Scanner stringScanner = new Scanner(messageStr);
                    stringScanner.next();
                    int deviceID = stringScanner.nextInt();
                    if(deviceID!=DEVICE_ID) {
                        debug("Found device "+deviceID);
                        nearby_devices.add(deviceID);
                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }


//    /**
//     * Thread responsible of starting a listening bluetooth socket and accepting new coming connection
//     */
//    private class BT_ServerThread extends Thread {
//        private final BluetoothServerSocket mmServerSocket;
//        MainActivity mainActivity;
//        public BT_ServerThread(MainActivity activity) {
//            mainActivity = activity;
//            BluetoothServerSocket tmp = null;
//
//            try {
//                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("Test", UUID.fromString(uuid));
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//            }
//            mmServerSocket = tmp;
//        }
//
//        public void run() {
//            BluetoothSocket socket = null;
//
//            while (true) {
//                try {
//                    Log.d(TAG, "SERVER: waiting for connection");
//                    socket = mmServerSocket.accept();
//
//                } catch (IOException e) {
//                    Log.e(TAG, e.getMessage());
//                    break;
//                }
//
//                if (socket != null) {
//                    int deviceID = findDevice_BT(socket.getRemoteDevice().getAddress());
//                    Log.d(TAG, "Connected to " + (deviceID));
//                    try {
//                        ConnectionThread thread = new ConnectionThread(mainActivity
//                                ,socket.getInputStream(), socket.getOutputStream()
//                                ,false, deviceID, false);
//                        thread.start();
//                    } catch (IOException e) {
//
//                    }
//                }
//            }
//
//            Log.d(TAG, "End of BT_ServerThread " + this);
//
//        }
//
//        public void cancel() {
//            Log.d(TAG, "Closing the socket");
//            try {
//                mmServerSocket.close();
//            } catch (IOException e) {
//            }
//        }
//    }
//    /**
//     * Thread responsible of connecting to a device
//     */
//    private class BT_ClientThread extends Thread {
//        private final BluetoothSocket mmSocket;
//        private final BluetoothDevice mmDevice;
//        MainActivity mainActivity;
//        public BT_ClientThread(MainActivity activity, BluetoothDevice device) {
//            BluetoothSocket tmp = null;
//            mmDevice = device;
//            mainActivity = activity;
//            try {
//                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid));
//
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//            }
//            mmSocket = tmp;
//        }
//
//        public void run() {
//            Log.i(TAG, "BEGIN mConnectThread " + this);
//            mBluetoothAdapter.cancelDiscovery();
//
//            try {
//                mmSocket.connect();
//                int deviceID = findDevice_BT(mmSocket.getRemoteDevice().getAddress());
//
//                ConnectionThread thread = new ConnectionThread(
//                        mainActivity,mmSocket.getInputStream(),
//                        mmSocket.getOutputStream(), true, deviceID, false);
//
//                thread.start();
//
//            } catch (IOException connectException) {
//                connectException.printStackTrace();
//                Log.e(TAG, connectException.getMessage());
//                try {
//                    mmSocket.close();
//                } catch (IOException closeException) {
//
//                    closeException.printStackTrace();
//                    Log.e(TAG, closeException.getMessage());
//                }
//
//                Log.d(TAG, "Failed connecting " + (findDevice_BT(mmSocket.getRemoteDevice().getAddress())));
//
//                return;
//            }
//
//        }
//
//        public void cancel() {
//            try {
//                mmSocket.close();
//            } catch (IOException e) {
//            }
//        }
//    }


}
