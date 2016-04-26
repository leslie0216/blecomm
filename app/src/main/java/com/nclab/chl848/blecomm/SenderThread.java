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
                    if (!BLEHandler.getInstance().CentralMessageSendQueue.isEmpty()) {
                        BLEHandler.CentralSendMessageInfo msg = BLEHandler.getInstance().CentralMessageSendQueue.remove(0);
                        if (msg.m_bluetoothGatt != null && msg.m_characteristic != null) {
                            int amountToSend = msg.m_value.length - msg.m_sendIndex;
                            boolean isCompleted = true;
                            int mtu = BLEHandler.getInstance().getPeripheralMTU(msg.m_bluetoothGatt.getDevice().getAddress()) - 4;
                            if (amountToSend > mtu) {
                                Log.d(BLEHandler.TAG, "SenderThread::run: data exceed the mtu, length = " + amountToSend);
                                msg.m_bluetoothGatt.requestMtu(amountToSend);
                                amountToSend = mtu;
                                isCompleted = false;
                            }

                            byte[] data = Arrays.copyOfRange(msg.m_value, msg.m_sendIndex, amountToSend);
                            byte[] prefix = new byte[1];
                            prefix[0] = (byte) (isCompleted ? 1 : 0);
                            byte[] dataToSend = new byte[data.length + prefix.length];
                            System.arraycopy(data, 0, dataToSend, 0, data.length);
                            System.arraycopy(prefix, 0, dataToSend, data.length, prefix.length);

                            msg.m_characteristic.setValue(dataToSend);
                            boolean didSend = msg.m_bluetoothGatt.writeCharacteristic(msg.m_characteristic);

                            if (!didSend) {
                                msg.m_sendCount++;
                                if (msg.m_sendCount < MAX_SEND_COUNT) {
                                    BLEHandler.getInstance().CentralMessageSendQueue.add(0, msg);
                                }
                            } else if (!isCompleted) {
                                msg.m_sendIndex += amountToSend;
                                BLEHandler.getInstance().CentralMessageSendQueue.add(0, msg);
                            }

                            m_canSend = false;
                            m_sendHandler.postDelayed(timerRunnable, SEND_PERIOD);
                        }
                    }
                }
            } else {

            }
        }
    }
}
