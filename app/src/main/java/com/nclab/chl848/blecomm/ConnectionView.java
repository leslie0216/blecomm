package com.nclab.chl848.blecomm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * show connection status
 */
public class ConnectionView extends View {
    Paint m_paint;
    private String m_message;
    private ConnectionActivity m_activity;
    private final IntentFilter m_intentFilter = new IntentFilter();

    private BroadcastReceiver m_broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BLEHandler.BLE_CONNECTION_UPDATE_ACTION.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle.containsKey(BLEHandler.BLE_EXTRA_DATA)) {
                    appendMessage(bundle.getString(BLEHandler.BLE_EXTRA_DATA));
                }
            } else if (BLEHandler.BLE_CONNECTION_AUTO_STOP_SCAN_ACTION.equals(action)) {
                m_activity.resetScanButton();
            } else if (BLEHandler.BLE_GATT_CONNECTED_ACTION.equals(action)) {
                if (BLEHandler.getInstance().getConnectionCount() > 0) {
                    appendMessage("Done!!!");
                    m_activity.enableDoneButton();
                }
            } else if (BLEHandler.BLE_GATT_DISCONNECTED_ACTION.equals(action)) {
                appendMessage("Disconnected!!!");
                if (BLEHandler.getInstance().getConnectionCount() == 0) {
                    m_activity.disableDoneButton();
                }
            } else if (BLEHandler.BLE_GATT_SERVICES_DISCOVERED_ACTION.equals(action)) {
                appendMessage("Service found!!!");
            } else if (BLEHandler.BLE_CONNECTION_NOT_SUPPORT_ACTION.equals(action)) {
                m_activity.disableScanButton();
            }
        }
    };

    public ConnectionView(Context context) {
        super(context);
        init();
    }

    public ConnectionView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }
    public ConnectionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        if (isInEditMode()) {
            m_paint = new Paint();
            m_message = "This device is working as";
            return;
        }
        m_paint = new Paint();
        m_message = "This device is working as";
        if (BLEHandler.getInstance().isCentral()) {
            m_message += " central";
        } else {
            m_message += " peripheral";
        }

        m_activity = (ConnectionActivity)getContext();

        m_intentFilter.addAction(BLEHandler.BLE_CONNECTION_UPDATE_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_CONNECTION_AUTO_STOP_SCAN_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_GATT_CONNECTED_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_GATT_DISCONNECTED_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_GATT_SERVICES_DISCOVERED_ACTION);
        m_intentFilter.addAction(BLEHandler.BLE_CONNECTION_NOT_SUPPORT_ACTION);
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

    public void appendMessage(String msg) {
        m_message += "\n" + msg;
        postInvalidate();
    }

}
