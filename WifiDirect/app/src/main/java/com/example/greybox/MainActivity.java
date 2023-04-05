package com.example.greybox;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/*
    JSGARVEY 03/03/23 - US#206 Citations:
    https://developer.android.com/training/connect-devices-wirelessly/wifi-direct#create-group
    Sarthi Technology - https://www.youtube.com/playlist?list=PLFh8wpMiEi88SIJ-PnJjDxktry4lgBtN3
 */
public class MainActivity extends FragmentActivity{

    Button btnDiscover, btnSend;
    ListView listView;
    TextView read_msg_box, connectionStatus;
    EditText writeMsg;
    ListView fileList;

    //Wifi Manager primary API for managing all aspects of WIFI connectivity
    WifiManager wifiManager;
    //Wifi P2p Manager provides specif API for managing WIFI p2p connectivity
    WifiP2pManager mManager;
    // A P2p channel that connects the app to the WIFI p2p framework
    WifiP2pManager.Channel mChannel;

    //Broadcast Receiver base class for code that receives and handles broadcast
    // intents sent by the context
    BroadcastReceiver mReceiver;
    // An Intent is a description of an operation to be performed.
    // A filter matches intents and describes the Intent values it matches.
    // Filters by characteristics of intents Actions, Data, and Categories
    IntentFilter mIntentFilter;

    // wifi p2p peers list
    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    // array holding names of devices
    String[] deviceNameArray;
    // the p2p peer array will be used to connect to a device
    WifiP2pDevice[] deviceArray;

    // Hardcoded value that indicates to the handler that message has been read
    static final int MESSAGE_READ = 1;
    ServerClass serverClass;
    ClientClass clientClass;
    WifiP2pInfo mWifiP2pInfo;

    //imported override method onCreate. Initialize the the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // call a the layout resource defining the UI
        setContentView(R.layout.activity_main);
        //pop up notifying if device supports wifi p2p
        if(getPackageManager().hasSystemFeature("android.hardware.wifi.direct")){
            Toast.makeText(getApplicationContext(), "WIFI DIRECT SUPPORTED", Toast.LENGTH_SHORT).show();
        }
        // creating objects
        initialWork();
        // adding listeners to the objects
        exListener();
    }

    // initial work for creating objects from onCreate()
    private void initialWork() {
        // create layout objects
        btnDiscover= findViewById(R.id.discover);
        btnSend= findViewById(R.id.sendButton);
        listView= findViewById(R.id.peerListView);
        read_msg_box= findViewById(R.id.readMsg);
        connectionStatus= findViewById(R.id.connectionStatus);
        writeMsg = findViewById(R.id.writeMsg);
        fileList = findViewById(R.id.fileList);

        // create wifi manager from the android app context system wifi services
        wifiManager= (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // create wifi p2p manager providing the API for managing Wifi peer-to-peer connectivity
        mManager = (WifiP2pManager) getApplicationContext().getSystemService(Context.WIFI_P2P_SERVICE);
        // a channel that connects the app to the wifi p2p framework.
        mChannel = mManager.initialize(this, getMainLooper(),null);
        // create wifi broadcast receiver to receive events from the wifi manager
        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        // indicates whether WiFi P2P is enabled
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // indicates that the available peer list has changed
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // indicates the state of Wifi P2P connectivity has changed
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // indicates this device's configuration details have changed
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

    }

    // implemented method for app object action listeners
    private void exListener(){

        // Discover button to discover peers on the same network
        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // listener discovering peers from broadcast channel
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                    // if listener created successfully display Discovery Started
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }
                    // if listener NOT created successfully display Discovery Failed
                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Discovery Failed"+i);
                    }
                });
            }
        });

        //Name of discovered peer turned into a button in the listView
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // this array is where the devices are stored for connections
                WifiP2pDevice device = deviceArray[i];
                // Config for setting up p2p connection
                WifiP2pConfig config = new WifiP2pConfig();
                // Set config device address from chosen device
                config.deviceAddress = device.deviceAddress;

                if(!(mWifiP2pInfo == null)){
                    if(mWifiP2pInfo.groupFormed){
                        InetAddress address =  mWifiP2pInfo.groupOwnerAddress;
                        config.deviceAddress = address.toString();
                        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                            // Called when device successfully connected
                            @Override
                            public void onSuccess() {
                                // Pop-up notifying device connected
                                Toast.makeText(getApplicationContext(),"CONNECTING TO "+address.toString(), Toast.LENGTH_SHORT).show();
                            }
                            // Called when device NOT successfully connected
                            @Override
                            public void onFailure(int i) {
                                // Pop-up notifying device NOT connected
                                Toast.makeText(getApplicationContext(),"NOT CONNECTED", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
                else{
                    mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                        // Called when device successfully connected
                        @Override
                        public void onSuccess() {
                            // Pop-up notifying device connected
                            Toast.makeText(getApplicationContext(),"CONNECTING TO "+device.deviceName, Toast.LENGTH_SHORT).show();
                        }
                        // Called when device NOT successfully connected
                        @Override
                        public void onFailure(int i) {
                            // Pop-up notifying device NOT connected
                            Toast.makeText(getApplicationContext(),"NOT CONNECTED", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        // Send button listener to send text message between peers
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // get text message from EditText Field
                String msg = writeMsg.getText().toString();
                if(mWifiP2pInfo.isGroupOwner){
                    // Class to handle send text message task
                    SendTask task  = new SendTask(msg, serverClass.getSendReceive());
                    task.execute();
                }else{
                    SendTask task  = new SendTask(msg, clientClass.getSendReceive());
                    task.execute();
                }

            }
        });
    }

    // Wifi P2P Manager peer list listener for collecting list of wifi peers
    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {

        // override method to find peers available
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            // if the peer previous peer list does not equal current peer list gotten by listener
            // the peers list has changed and we want to store the new list instead
            if(!peerList.getDeviceList().equals(peers)){
                Toast.makeText(getApplicationContext(), "Peers Changed", Toast.LENGTH_SHORT).show();
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                //store peers list device names to be display and add to device array to be selected
                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;
                for(WifiP2pDevice device : peerList.getDeviceList()){
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                // add all the device names to an adapter then add the adapter to the layout listview
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,deviceNameArray);
                listView.setAdapter(adapter);
            }

            // if no peers found pop-up "No Device Found"
            if(peers.size() == 0){
                Toast.makeText(getApplicationContext(), "No Device Found", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

    // interface for callback invocation when connection info is available
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        // If the connection info is available
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            mWifiP2pInfo = wifiP2pInfo;
            // Get Host Ip Address
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            // If the connection group exists and the device is connection host
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("HOST");
//                serverClass = new ServerClass(handler);
//                serverClass.start();
            // If only the connection group exists
            } else if (wifiP2pInfo.groupFormed) {
                connectionStatus.setText("CLIENT");
//                clientClass = new ClientClass(groupOwnerAddress, handler);
//                clientClass.start();

            }
        }
    };


    // A Handler allows you to send and process Message and Runnable objects associated with a
    // thread's MessageQueue. Each Handler instance is associated with a single thread and that
    // thread's message queue.
    // Handler.Callback interface you can use when instantiating a Handler to avoid having to
    // implement your own subclass of Handler.
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            // msg.what identifies what message is about
            switch(msg.what){
                // If the message is a text message to be read case=1
                case MESSAGE_READ:
                    // cast the message object as an byte array to store the message
                    byte[] readBuff = (byte[]) msg.obj;
                    // store the byte array as a String to be printed
                    String tempMsg = new String(readBuff,0,msg.arg1);
                    // Add message to devices textview
                    read_msg_box.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    // When activity enters the resume state after onCreate and onStart
    @Override
    protected  void onResume(){
        super.onResume();
        registerReceiver(mReceiver,mIntentFilter);
    }

    // Systems call this method when the user leaves the activity meaning when
    // the activity is no longer in the foreground.
    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(mReceiver);
    }


}
