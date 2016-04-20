package com.nclab.chl848.blecomm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * Test view
 */
public class TestView extends View {

    private class PingInfo {
        public String m_token;
        public long m_startTime;
        public List<Double> m_timeIntervals;
        public int m_totalCount;
        public int m_currentCount;
        public int m_number;

        PingInfo() {
            m_timeIntervals = new ArrayList<>();
        }
    }


    private Paint m_paint;
    private TestActivity m_activity;
    private String m_message;
    private String m_status;

    private boolean m_isPing;
    private Hashtable<String, PingInfo> m_pingDict = null;
    private List<Double> m_timerArray = null;
    private int m_totalCount;
    private Handler m_pingHandler;



    private final IntentFilter m_intentFilter = new IntentFilter();

    private BroadcastReceiver m_broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BLEHandler.BLE_CONNECTION_UPDATE_ACTION.equalsIgnoreCase(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle.containsKey(BLEHandler.BLE_EXTRA_DATA)) {
                    updateStatus(bundle.getString(BLEHandler.BLE_EXTRA_DATA));
                }
            } else if (BLEHandler.BLE_GATT_CONNECTED_ACTION.equalsIgnoreCase(action)) {
                updateStatus("Connected!!!");
                m_activity.setPingButtonEnabled(true);
            } else if (BLEHandler.BLE_GATT_DISCONNECTED_ACTION.equalsIgnoreCase(action)) {
                if (BLEHandler.getInstance().getConnectionCount() == 0) {
                    updateStatus("Disconnected!!!");
                    m_activity.setPingButtonEnabled(false);
                }
            } else if (BLEHandler.BLE_RECEIVED_DATA_ACTION.equalsIgnoreCase(action)) {
                handleReceivedDataAction(intent);
            }
        }
    };

    public TestView(Context context) {
        super(context);

        m_paint = new Paint();
        m_message = "";
        if (BLEHandler.getInstance().getConnectionCount() > 0) {
            m_status = "Connected";
        } else {
            m_status = "Not Connected";
        }
        m_activity = (TestActivity)context;

        m_intentFilter.addAction(BLEHandler.BLE_CONNECTION_UPDATE_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_GATT_CONNECTED_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_GATT_DISCONNECTED_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_RECEIVED_DATA_ACTION);
    }

    public void registerBLEReceiver() {
        m_activity.registerReceiver(m_broadcastReceiver, m_intentFilter);
    }

    public void unRegisterBLEReceiver() {
        m_activity.unregisterReceiver(m_broadcastReceiver);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        drawMessage(canvas);
        drawStatus(canvas);
    }

    private void drawMessage(Canvas canvas) {
        m_paint.setTextSize(30);
        m_paint.setColor(Color.BLUE);
        m_paint.setStrokeWidth(1);
        m_paint.setStyle(Paint.Style.FILL_AND_STROKE);
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float x = displayMetrics.widthPixels * 0.1f;
        float y = displayMetrics.heightPixels * 0.1f;
        for (String line: m_message.split("\n")) {
            canvas.drawText(line, x, y, m_paint);
            y += m_paint.descent() - m_paint.ascent();
        }
    }

    private void drawStatus(Canvas canvas) {
        m_paint.setTextSize(30);
        m_paint.setColor(Color.RED);
        m_paint.setStrokeWidth(1);
        m_paint.setStyle(Paint.Style.FILL_AND_STROKE);
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float x = displayMetrics.widthPixels * 0.45f;
        float y = displayMetrics.heightPixels * 0.9f;
        canvas.drawText(m_status, x, y, m_paint);
    }

    public void updateStatus(String s) {
        m_status = s;
        postInvalidate();
    }

    private void handleReceivedDataAction(Intent intent) {
        Bundle bundle = intent.getExtras();

        byte[] data = bundle.getByteArray(BLEHandler.BLE_EXTRA_DATA);
        long receiveTime = bundle.getLong(BLEHandler.BLE_EXTRA_DATA_RECEIVE_TIME);

        if (data != null && data.length != 0 && receiveTime != 0) {
            try {
                Message.PingMessage message = Message.PingMessage.parseFrom(data);

                if (message.getMessageType() == Message.PingMessage.MsgType.RESPONSE) {
                    String token = message.getToken();

                    PingInfo info = m_pingDict.get(token);
                    if (info.m_totalCount == info.m_currentCount) {
                        Log.d(BLEHandler.TAG, "handleReceivedDataAction: token over received");
                        return;
                    }

                    //Log.d(BLEHandler.TAG, "Receive time(r) = " + receiveTime + " with token : " + token);
                    //Log.d(BLEHandler.TAG, "Start time(r) = " + info.getStartTime() + " with token : " + token);
                    //Log.d(BLEHandler.TAG, "ResponseTime =  " + message.getResponseTime());

                    // calculate time interval in millisecond - 1 millisecond = 1000000 nanosecond
                    double timeInterval = ((double)receiveTime) / 1000000.0 - ((double)info.m_startTime)/1000000.0 - message.getResponseTime();
                    Log.d(BLEHandler.TAG, "handleReceivedDataAction token : " + token + ", timeInterval : " + timeInterval);
                    if (timeInterval > 300) {
                        Log.d(BLEHandler.TAG, "!!!High latency!!!");
                    } else if(timeInterval < 0) {
                        Log.d(BLEHandler.TAG, "!!!Negative time value!!!");
                    }

                    m_timerArray.add(timeInterval);
                    info.m_timeIntervals.add(timeInterval);
                    info.m_currentCount += 1;
                    m_pingDict.put(token, info);

                    if (isPing()) {
                        m_message = "current : " + timeInterval + "\n"
                                + "received count : " + m_timerArray.size() + "\n"
                                + "total count : " + m_totalCount;
                        postInvalidate();
                    }

                } else if (message.getMessageType() == Message.PingMessage.MsgType.PING) {

                    Message.PingMessage.Builder mb = Message.PingMessage.newBuilder();
                    mb.setToken(message.getToken());
                    mb.setMessageType(Message.PingMessage.MsgType.RESPONSE);
                    mb.setIsReliable(message.getIsReliable());

                    double responseTime = ((double)System.nanoTime())/1000000.0 - ((double)receiveTime) / 1000000.0;
                    mb.setResponseTime(responseTime);

                    Message.PingMessage msg = mb.build();
                    if (BLEHandler.getInstance().isCentral()) {
                        if (BLEHandler.getInstance().sendDataToPeripheral(bundle.getString(BLEHandler.BLE_EXTRA_DATA_ADDRESS), msg.toByteArray())) {
                            Log.d(BLEHandler.TAG, "handleReceivedDataAction: send response with token : " + msg.getToken() + " response time : " + responseTime);
                        } else {
                            Log.d(BLEHandler.TAG, "handleReceivedDataAction: write characteristic failed! token : " + msg.getToken());
                        }
                    } else {
                        if (BLEHandler.getInstance().sendDataToCentral(msg.toByteArray())) {
                            Log.d(BLEHandler.TAG, "handleReceivedDataAction: send response with token : " + msg.getToken() + " response time : " + responseTime);
                        } else {
                            Log.d(BLEHandler.TAG, "handleReceivedDataAction: write characteristic failed! token : " + msg.getToken());
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                m_message = ex.getLocalizedMessage();
                postInvalidate();
            }
        }
    }

    //region PING
    public boolean isPing() {
        return m_isPing;
    }

    public void startPing() {
        m_isPing = true;

        if (m_timerArray == null) {
            m_timerArray = new ArrayList<>();
        } else {
            m_timerArray.clear();
        }

        if (m_pingDict == null) {
            m_pingDict = new Hashtable<>();
        } else {
            m_pingDict.clear();
        }

        m_totalCount = 0;

        if (m_pingHandler == null) {
            m_pingHandler = new Handler();
        }
        m_pingHandler.postDelayed(timerRunnable, 0);
    }

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            doPing();
            if (m_isPing) {
                m_pingHandler.postDelayed(this, 100);
            }
        }
    };

    public void stopPing(boolean isExit) {
        m_isPing = false;
        if (m_pingHandler != null) {
            m_pingHandler.removeCallbacks(timerRunnable);
        }

        if (!isExit) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    calculateResult();
                }
            }, 500);
        }
    }

    private void doPing() {
        Message.PingMessage.Builder mb = Message.PingMessage.newBuilder();
        //String currentToken  = UUID.randomUUID().toString();
        String currentToken = String.valueOf(m_totalCount);
        Log.d(BLEHandler.TAG, "sendMessage: token : " + currentToken);
        mb.setToken(currentToken);
        mb.setMessageType(Message.PingMessage.MsgType.PING);
        mb.setResponseTime(0.0);
        mb.setIsReliable(false);

        Message.PingMessage msg = mb.build();

        long startTime = System.nanoTime();
        if (BLEHandler.getInstance().isCentral()) {
            BLEHandler.getInstance().sendDataToAllPeripherals(msg.toByteArray());
        } else {
            BLEHandler.getInstance().sendDataToCentral(msg.toByteArray());
        }

        PingInfo info = new PingInfo();
        info.m_startTime = startTime;
        info.m_token = currentToken;
        info.m_totalCount = BLEHandler.getInstance().getConnectionCount();
        info.m_currentCount = 0;
        info.m_number = m_totalCount + 1;
        m_totalCount += info.m_totalCount;

        m_pingDict.put(currentToken, info);
    }

    private void calculateResult() {
        int total = 0;
        int received = 0;
        double totalTime = 0.0;
        double min = 10000.0;
        double max = 0.0;
        List<Double> allTimes = new ArrayList<>();
        Enumeration<PingInfo> values = m_pingDict.elements();
        while (values.hasMoreElements()) {
            PingInfo info = values.nextElement();
            total += info.m_totalCount;
            received += info.m_currentCount;

            for (Double time : info.m_timeIntervals) {
                totalTime += time;
                if (time > max) {
                    max = time;
                }
                if (time < min) {
                    min = time;
                }
                allTimes.add(time);
            }
        }

        double average = totalTime / allTimes.size();

        double sumOfSquaredDifferences = 0.0;
        for (Double time : allTimes) {
            double difference = time - average;
            sumOfSquaredDifferences += difference * difference;
        }
        double std = Math.sqrt(sumOfSquaredDifferences / allTimes.size());
        double lossRate = (1.0 - (double)received/total) * 100.0;

        m_message = "total : " + total + "\n"
                + "received : " + received + "\n"
                + "loss rate : " + lossRate + "%" + "\n"
                + "min : " + min + "\n"
                + "max : " + max + "\n"
                + "average : " + average + "\n"
                + "stdev : " + std;

        postInvalidate();
    }
    //endregion
}
