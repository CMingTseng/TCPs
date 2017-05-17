package idv.neo.tcp.client;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import idv.neo.data.MessageData;
import idv.neo.data.MessageInfo;
import idv.neo.utils.Communication;
import idv.neo.utils.GsonConvertUtils;
import idv.neo.utils.ObservableInterface;

/**
 * Created by Neo on 2017/4/20.
 */

public class TCPClientRunnable implements Runnable, ObservableInterface {
    private static final String TAG = "TCPClientRunnable2";
    //100K
    private static final int BUFF_SIZE = 1024 * 100;
    private boolean mRunning = false;
    private String mServerIP;
    private int mServerPort;
    private Socket mSocket;
    private Communication mCommunication;
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

    private ContentObserver mTCPClientObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "mTCPClientThreadObserver onChange Uri : " + uri);
            final String type = uri.getQueryParameter("type");
            Log.d(TAG, " Show type  : " + type);
            final String property = uri.getQueryParameter("property");
            Log.d(TAG, " Show property : " + property);
            if (type == null) {
                return;
            } else if (type.equals("sensorevent")) {
                Log.d(TAG, " get info  : " + property);
                if (mCommunication!=null){
                    mCommunication.sendMessage(property);
                }else {
                    callDispStopSensors();
//                    callDispReConnect();
                }
            }
        }
    };

    private void dispatchChange(boolean selfChange, String type, String property) {
        Log.d(TAG, "dispatchChange info :" + type + " : " + property);
        mGeneralObservable.dispatchChangeCompat(
                selfChange, Uri.parse("content://TCP/Client?" + (type == null ? "" : "type=" + type) + (property == null ? "" : "&property=" + property))
        );
    }

    public ContentObserver getTCPClientObserver() {
        return mTCPClientObserver;
    }

    public TCPClientRunnable(String serverip, int serverport, ContentObserver targetObserver) {
        mServerIP = serverip;
        mServerPort = serverport;
        registerContentObserver(targetObserver);
    }

    @Override
    public void run() {
        Log.d(TAG, " get current run thread : " + Thread.currentThread().getId());
        try {
            mSocket = new Socket(mServerIP, mServerPort);
            mRunning = true;
            mCommunication = new Communication(mSocket);
            while (mRunning) {
                try {
                    final String response = mCommunication.getResponse();
                    boolean first = false;
                    if (response == null | !response.startsWith("{")) {
                        break;
                    }
                    final MessageData raw = GsonConvertUtils.stringToType(response, MessageData.class);
                    final MessageData outmessage = new MessageData();
                    if (raw != null) {
                        MessageInfo info = raw.getData();
                        Log.d(TAG, "Get Server info : " + info.getTypevalue());
                        if (info.getType() == MessageInfo.IDENTIFY && info.getTypename().equals(MessageInfo.MESSAGE_IDENTIFY_SERVER)) {
                            first = true;
                            info = new MessageInfo(MessageInfo.IDENTIFY, MessageInfo.MESSAGE_IDENTIFY_CLIENT, "master");
                            outmessage.setData(info);
                            first = false;
                        } else if (info.getType() == MessageInfo.IDENTIFY && info.getTypename().equals(MessageInfo.MESSAGE_IDENTIFY_SERVER_PW)) {
                            Log.d(TAG, " reg to server ok  ");
                            info = new MessageInfo(MessageInfo.IDENTIFY, MessageInfo.MESSAGE_IDENTIFY_CLIENT, "go");
                            outmessage.setData(info);
                            dispatchChange(false, "startsensor", "startsensor");
                        }
                        mCommunication.sendMessage(outmessage.toString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "ServerSocket MSG_SERVER_START_ERROR " + e);
        }
    }

    private void callDispStopSensors(){
        dispatchChange(false, "stopsensors", "stopsensors");
    }

    private void callDispReConnect(){
        dispatchChange(false, "reconnecttoserver", "reconnecttoserver");
    }
}
