package idv.neo.tcp.server;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import idv.neo.data.MessageData;
import idv.neo.data.MessageInfo;
import idv.neo.utils.Communication;
import idv.neo.utils.GsonConvertUtils;
import idv.neo.utils.ObservableInterface;

/**
 * Created by Neo on 2017/4/18.
 */

public class TCPServerThread extends Thread implements ObservableInterface {
    private static final String TAG = "TCPServerThread";
    private static final int BUFF_SIZE = 1024 * 100;
    private ServerSocket mSocket = null;
    private boolean mRunning = false;
    private int mPort;
    private static ArrayList<Socket> mSocketlist = new ArrayList<Socket>();

    public final void registerContentObserver(ContentObserver observer) {
        try {
            mGeneralObservable.registerObserver(observer);
        } catch (IllegalStateException ignored) {
            Log.d(TAG, " registerContentObserver Exception :" + ignored);
        }
    }

    public final void unregisterContentObserver(ContentObserver observer) {
        try {
            mGeneralObservable.unregisterObserver(observer);
        } catch (IllegalStateException ignored) {
            Log.d(TAG, " unregisterContentObserver Exception :" + ignored);
        }
    }

    private ContentObserver mTCPServerObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
        }
    };

    private void dispatchChange(boolean selfChange, String type, String property) {
        Log.d(TAG, "dispatchChange info :" + type + " : " + property);
        mGeneralObservable.dispatchChangeCompat(
                selfChange, Uri.parse("content://TCP/Server?" + (type == null ? "" : "type=" + type) + (property == null ? "" : "&property=" + property))
        );
    }

    public TCPServerThread(int listenport, ContentObserver targetObserver) {
        this.mPort = listenport;
        registerContentObserver(targetObserver);
    }

    @Override
    public void run() {
        Log.d(TAG, " get current run thread : " + Thread.currentThread().getId());
        try {
            if (mSocket == null) {
                mSocket = new ServerSocket(mPort);
                dispatchChange(false, "port", String.valueOf(mSocket.getLocalPort()));
                Log.d(TAG, "ServerSocket start at port");
            }
            mSocket.setReceiveBufferSize(BUFF_SIZE);
            mSocket.setReuseAddress(true);
            mSocket.setSoTimeout(100000);
            mRunning = true;
            new Thread(checkclentstatus).start();
            while (mRunning) {
                try {
                    final Socket client = mSocket.accept();
                    Log.d(TAG, "ServerSocket get a new  client come~~~ " + client + "_info : " + client.getInetAddress().getHostAddress());
                    if (client.isConnected()) {
                        // new client
                        createNewThread(client);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "ServerSocket MSG_SERVER_START_ERROR " + e);
        }
    }

    public void createNewThread(final Socket clientSocket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, " get current run thread : " + Thread.currentThread().getId());
                final String selfip = clientSocket.getLocalAddress().getHostAddress().replace("/", "");
                Log.d(TAG, "selfip     :  " + selfip);
                final String clientip = clientSocket.getInetAddress().getHostAddress().replace("/", "");
                Log.d(TAG, "clientip    :  " + clientip);
                dispatchChange(false, "client", clientip);
                dispatchChange(false, "servergetmessage", "has one client  : " + clientip + " coming...");
                while (true) {
                    if (!mSocketlist.contains(clientSocket)) {
                        Communication communication = new Communication(clientSocket);
                        communication.sendHelloPackage();
                        Log.d(TAG, " new client   first add to array :  " + clientip);
                        mSocketlist.add(clientSocket);
                        dispatchChange(false, "servergetmessage", "the new client  : " + clientip + " add...");
                    } else if (mSocketlist.contains(clientSocket)) {
                        Communication communication = new Communication(clientSocket);
                        final String request = communication.getResponse();
                        if (request == null | !request.startsWith("{")) {
                            break;
                        }
                        Log.d(TAG, "Get Client - " + clientip + " new message : " + request);
                        final MessageData raw = GsonConvertUtils.stringToType(request, MessageData.class);
                        final MessageData outmessage = new MessageData();
                        if (raw != null) {
                            MessageInfo info = raw.getData();
                            Log.d(TAG, "Get Server info : " + info.getTypevalue());
                            if (info.getType() == MessageInfo.IDENTIFY && info.getTypename().equals(MessageInfo.MESSAGE_IDENTIFY_CLIENT)) {
                                dispatchChange(false, "setclient", "check client " + clientip + " ok. start Communication");
                                info = new MessageInfo(MessageInfo.IDENTIFY, MessageInfo.MESSAGE_IDENTIFY_SERVER_PW,selfip,clientip, "reg123");
                                outmessage.setData(info);
                                communication.sendMessage(outmessage.toString());
                            } else {
                                dispatchChange(false, "servergetmessage", info.toString());
                            }
                        }
                    }
                }
            }
        }).start();
    }

    private Runnable checkclentstatus = new Runnable() {
        @Override
        public void run() {
            try {
                while (mRunning) {
                    Log.d(TAG, " checkclentstatus get current run thread : " + Thread.currentThread().getId());
                    Thread.sleep(3000);
                    for (Socket close : mSocketlist) {
                        if (isServerClose(close)) {
                            mSocketlist.remove(close);
                            Log.d(TAG, " checkclentstatus remove : " );
                        }
                        if (mSocketlist.size() == 0) {
                            mRunning = false;
                            mSocket.close();
                            Log.d(TAG, " checkclentstatus Socket.close : " );
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private static Boolean isServerClose(Socket socket) {
        Log.d(TAG, " get current run thread : " + Thread.currentThread().getId());
        try {
            socket.sendUrgentData(0);
            Log.d(TAG, " cleint close");
            return false;
        } catch (Exception e) {
            Log.d(TAG, " cleint connect ");
            return true;
        }
    }
}
