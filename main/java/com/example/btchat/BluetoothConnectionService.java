package com.example.btchat;


import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.nfc.Tag;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {

    private static final String TAG = "BluetoothConnectionServ", appName = "BTChat";

    private static final UUID MY_UUID_INSECURE = UUID.fromString("fe9ba54e-e224-11eb-ba80-0242ac130004");

    private final BluetoothAdapter mBluetoothAdapter;

    Context mContext;

    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private BluetoothDevice mBluetoothDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;

    private ConnectedThread mConnectedThread;

    public BluetoothConnectionService(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    public BluetoothConnectionService(Context context, boolean dontStart) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private class AcceptThread extends Thread {
        private  BluetoothServerSocket mBluetoothServerSocket;
        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread: setting up a server using uuid: " + MY_UUID_INSECURE);
            } catch(IOException e) {}
            mBluetoothServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "run: AcceptThread Running");
            BluetoothSocket btSocket = null;
            Log.d(TAG, "run: RFCOM server socket start");
            try{
                btSocket = mBluetoothServerSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread, run IOException: " + e.getMessage());
            }
            if(btSocket != null) {
                connected(btSocket, mBluetoothDevice);
            }
            Log.d(TAG, "END AcceptThread");
        }

        public void cancel() {
            Log.d(TAG, "cancel: cancelling AcceptThread");
            try{
                mBluetoothServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel, ACcepthThread: failed: " + e.getMessage());
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mBtSocket;
        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started");
            mBluetoothDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket tmp = null;
            Log.i(TAG, "Run connectThread");
            try {
                Log.d(TAG, "ConnectThread: trying to create insecurerfcommsocket with uuid: " + deviceUUID);
                tmp = mBluetoothDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: could not create insecureRfcommSocket " + e.getMessage());
            }
            mBtSocket = tmp;
            mBluetoothAdapter.cancelDiscovery();
            try {

                mBtSocket.connect();
                Log.i(TAG, "run: ConnnectThread connected ");
            } catch (IOException e) {
                try{
                    mBtSocket.close();
                    Log.d(TAG, "socket closed");
                } catch (IOException e1) {
                    Log.e(TAG, "ConnectThread: run: unable to close socket " + e1.getMessage());
                }
                Log.d(TAG, "could not connect to " + MY_UUID_INSECURE);
            }

            connected(mBtSocket, mBluetoothDevice);
        }

        public void cancel() {
            try{
                Log.d(TAG,  "cancel: Closing Client Socket");
                mBtSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close of mBtSocket failed " + e.getMessage());
            }
        }

    }

    public synchronized void start () {
        Log.d(TAG, "start");
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if(mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }

    }

    public void  startClient(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "start Client : started");
        // mProgressDialog = ProgressDialog.show(mContext, "connwctiongBluetooth");
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mBtSocket;
        private final InputStream mInStream;
        private final OutputStream mOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread : starting");

            mBtSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmpIn = mBtSocket.getInputStream();
                tmpOut = mBtSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "failed to get inputStream: " + e.getMessage());
            }
            mInStream = tmpIn;
            mOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while(true) {
                try {
                    bytes = mInStream.read(buffer);
                    String inMsg = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + inMsg);
                } catch (IOException e) {
                    Log.e(TAG, "run connectedThread: inStream failed: " + e.getMessage());
                    break;
                }
            }

        }

        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "writing to output: " + text);
            try {
                mOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "write: Error writing to output stream " + e.getMessage());
            }
        }

        public void cancel() {
            try {
                mBtSocket.close();
            } catch (IOException e) {}
        }

    }

    private void connected(BluetoothSocket btSocket, BluetoothDevice mBluetoothDevice) {
        Log.d(TAG, "connected: starting");

        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

    }

    public void write(byte[] out) {
        Log.d(TAG, "write called");
        mConnectedThread.write(out);
    }

}
