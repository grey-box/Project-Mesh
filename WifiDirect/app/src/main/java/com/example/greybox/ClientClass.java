package com.example.greybox;


import android.os.Handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

// Client class to be made into own class soon
public class ClientClass extends Thread{
    Socket socket;
    String hostAdd;
    SendReceive sendReceive;
    Handler handler;
    // Constructor passed the server host IpAddress
    public ClientClass(InetAddress hostAddress, Handler _handler){
        hostAdd = hostAddress.getHostAddress();
        socket = new Socket();
        this.handler = _handler;
    }

    @Override
    public void run() {
        try {
            // try to connect socket to host socket port
            socket.connect(new InetSocketAddress(hostAdd,8888), 500);
            // Create sendRecieve task to handle socket stream operations
            sendReceive = new SendReceive(socket, handler);
            // Run the sendReceive object
            sendReceive.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SendReceive getSendReceive(){
        return sendReceive;
    }


}