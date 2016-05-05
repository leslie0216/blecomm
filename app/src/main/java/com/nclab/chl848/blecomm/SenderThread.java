package com.nclab.chl848.blecomm;

import android.os.Handler;
import android.util.Log;

import java.util.Arrays;

/**
 * Send ble message
 */
public class SenderThread extends Thread {
    private static final long SEND_PERIOD = 500;
    private static final int MAX_SEND_COUNT = 3;

    private boolean m_canSend;
    private Handler m_sendHandler = new Handler();

    public void resetSendFlag() {
        m_canSend = true;
    }

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            resetSendFlag();
        }
    };

    @Override
    public void run() {
        while (!isInterrupted()){
            if (m_canSend) {
                m_sendHandler.removeCallbacks(timerRunnable);

                if (BLEHandler.getInstance().isCentral()) {
                    synchronized (BLEHandler.getInstance().CentralMessageSendQueue) {
                        if (!BLEHandler.getInstance().CentralMessageSendQueue.isEmpty()) {
                            BLEHandler.CentralSendMessageInfo msg = BLEHandler.getInstance().CentralMessageSendQueue.get(0);
                            if (msg.m_bluetoothGatt != null && msg.m_characteristic != null) {
                                int amountToSend = msg.m_value.length;
                                int mtu = BLEHandler.getInstance().getPeripheralMTU(msg.m_bluetoothGatt.getDevice().getAddress()) - 4;
                                if (amountToSend > mtu) {
                                    Log.d(BLEHandler.TAG, "SenderThread::run: data exceed the mtu, length = " + amountToSend);
                                }

                                m_canSend = false;

                                msg.m_characteristic.setValue(msg.m_value);
                                boolean didSend = msg.m_bluetoothGatt.writeCharacteristic(msg.m_characteristic);
                                Log.d(BLEHandler.TAG, "SenderThread::run: sendData: byteMsg size : " + msg.m_value.length);

                                if (!didSend) {
                                    Log.d(BLEHandler.TAG, "SenderThread::run: data send failed, time =  " + msg.m_sendCount);
                                    msg.m_sendCount++;
                                    if (msg.m_sendCount > MAX_SEND_COUNT) {
                                        Log.d(BLEHandler.TAG, "SenderThread::run: dismiss data, time =  " + msg.m_sendCount);
                                        BLEHandler.getInstance().CentralMessageSendQueue.remove(0);
                                    }
                                    m_canSend = true;
                                } else {
                                    m_sendHandler.postDelayed(timerRunnable, SEND_PERIOD);
                                }
                            }
                        }
                    }
                }
            } else {
                /*
                synchronized (BLEHandler.getInstance().PeripheralMessageQueue) {
                    if (!BLEHandler.getInstance().PeripheralMessageQueue.isEmpty()) {
                        m_canSend = false;
                        BLEHandler.PeripheralSendMessageInfo msg = BLEHandler.getInstance().PeripheralMessageQueue.get(0);
                        boolean didSend = BLEHandler.getInstance().executeSendDataToCentral(msg.m_value);
                        if (!didSend) {
                            Log.d(BLEHandler.TAG, "SenderThread::run: data send failed, time =  " + msg.m_sendCount);
                            msg.m_sendCount++;
                            if (msg.m_sendCount > MAX_SEND_COUNT) {
                                Log.d(BLEHandler.TAG, "SenderThread::run: dismiss data, time =  " + msg.m_sendCount);
                                BLEHandler.getInstance().PeripheralMessageQueue.remove(0);
                            }
                            m_canSend = true;
                        } else {
                            m_sendHandler.postDelayed(timerRunnable, SEND_PERIOD);
                        }
                    }
                }*/
            }
        }
    }
}
