package com.nclab.chl848.blecomm;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * bluetooth low energy handler
 */
public class BLEHandler {

    public class CentralSendMessageInfo {
        public BluetoothGatt m_bluetoothGatt;
        public BluetoothGattCharacteristic m_characteristic;
        public byte[] m_value;
    }

    private class PeripheralInfo {
        public BluetoothGatt m_bluetoothGatt;
        public BluetoothGattCharacteristic m_writeCharacteristic;
        public BluetoothGattCharacteristic m_readCharacteristic;
        public String m_name;
        public int m_mtu;
    }

    private class PeripheralWriteRequestData{
        private String  m_deviceAddress;
        private String m_UUID;
        private boolean m_isCharacter;
        List<byte[]> byteArray;
        public PeripheralWriteRequestData(String address,String uuid, boolean isCharacter){
            m_deviceAddress = address;
            m_UUID = uuid;
            byteArray = new ArrayList<>();
            m_isCharacter = isCharacter;
        }

        public boolean isCharacter(){return m_isCharacter;}
        public String getUUID(){return m_UUID;}
        public String getDeviceAddress(){return m_deviceAddress;}
        public void clearData(){
            byteArray.clear();
        }
        public void addData(byte[] array){
            byteArray.add(array);
        }

        public byte[] getFullData(){
            byte[] retArray;
            int totalSize = 0;

            for(int i=0; i < byteArray.size();i++){
                totalSize = totalSize + byteArray.get(i).length;
            }

            int copuCounter = 0;
            if(totalSize > 0) {
                retArray = new byte[totalSize];
                for(int ii=0; ii < byteArray.size();ii++){
                    byte[] tmpArr = byteArray.get(ii);
                    System.arraycopy(tmpArr, 0, retArray,copuCounter,tmpArr.length);
                    copuCounter = copuCounter + tmpArr.length;
                }
            }else{
                retArray = new byte[]{};
            }
            return retArray;
        }
    }

    private class MessageData {
        private String  m_deviceAddress;
        List<byte[]> byteArray;
        public MessageData(String address){
            m_deviceAddress = address;
            byteArray = new ArrayList<>();
        }

        public String getDeviceAddress(){return m_deviceAddress;}
        public void clearData(){
            byteArray.clear();
        }
        public void addData(byte[] array){
            byteArray.add(array);
        }

        public byte[] getFullData(){
            byte[] retArray;
            int totalSize = 0;

            for(int i=0; i < byteArray.size();i++){
                totalSize = totalSize + byteArray.get(i).length;
            }

            int copuCounter = 0;
            if(totalSize > 0) {
                retArray = new byte[totalSize];
                for(int ii=0; ii < byteArray.size();ii++){
                    byte[] tmpArr = byteArray.get(ii);
                    System.arraycopy(tmpArr, 0, retArray,copuCounter,tmpArr.length);
                    copuCounter = copuCounter + tmpArr.length;
                }
            }else{
                retArray = new byte[]{};
            }
            return retArray;
        }
    }

    //region CONSTANTS
    public static final String TAG = "BLECOMM";
    public static final String BLE_CONNECTION_UPDATE_ACTION = "blecomm.chl848.nclab.com.BLE_CONNECTION_UPDATE_ACTION";
    public static final String BLE_CONNECTION_AUTO_STOP_SCAN_ACTION = "blecomm.chl848.nclab.com.BLE_CONNECTION_AUTO_STOP_SCAN_ACTION";
    public static final String BLE_CONNECTION_NOT_SUPPORT_ACTION = "blecomm.chl848.nclab.com.BLE_CONNECTION_NOT_SUPPORT_ACTION";

    public final static String BLE_GATT_CONNECTED_ACTION = "blecomm.chl848.nclab.com.le.ACTION_GATT_CONNECTED";
    public final static String BLE_GATT_DISCONNECTED_ACTION = "blecomm.chl848.nclab.com.ACTION_GATT_DISCONNECTED";
    public final static String BLE_GATT_SERVICES_DISCOVERED_ACTION = "blecomm.chl848.nclab.com.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String BLE_RECEIVED_DATA_ACTION = "blecomm.chl848.nclab.com.BLE_RECEIVED_DATA_ACTION";
    public final static String BLE_EXTRA_DATA_RECEIVE_TIME = "blecomm.chl848.nclab.com.BLE_EXTRA_DATA_RECEIVE_TIME";
    public final static String BLE_EXTRA_DATA = "blecomm.chl848.nclab.com.EXTRA_DATA";
    public final static String BLE_EXTRA_DATA_ADDRESS = "blecomm.chl848.nclab.com.BLE_EXTRA_DATA_ADDRESS";

    private static final long SCAN_PERIOD = 10000;
    public static final String TRANSFER_SERVICE_UUID = "A3EC42C6-ADF8-48A8-8F88-2E32AD32667B";
    public static final String TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID = "481AD972-35A9-44F8-9C9A-9DF1644E1E1E";
    public static final String TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID = "A147F9FE-0914-4706-9A07-20AAC9D7AB92";
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private static final int DEFAULT_MTU = 23;
    private static final int MAX_MTU = 512;
    private static final int SYSTEM_RESERVED_MTU = 3;

    //endregion

    //region COMMON VARS
    private Activity m_currentActivity;
    private BluetoothManager m_bluetoothManager;
    private BluetoothAdapter m_bluetoothAdapter;
    private boolean isCentral;
    private boolean m_isScanningOrAdvertising;
    private boolean m_isInit = false;
    private List<MessageData> m_recMsgArray = new ArrayList<>();
    //private SenderThread m_senderThread;
    //endregion

    //region CENTRAL VARS
    private BluetoothLeScanner m_LEScanner;
    private ScanSettings m_scanSettings;
    private List<ScanFilter> m_filters;
    private Handler m_scanHandler;
    private ScanCallback m_scanCallback;
    private BluetoothGattCallback m_gattCallback;
    private Hashtable<String, PeripheralInfo> m_peripheralDevices = null;
    public final List<CentralSendMessageInfo> CentralMessageSendQueue = new LinkedList<>();
    //endregion

    //region PERIPHERAL VARS
    private BluetoothGattServer m_bluetoothGattServer;
    private BluetoothGattServerCallback m_gattServerCallback;
    private BluetoothLeAdvertiser m_advertiser;
    private AdvertiseCallback m_advertisingCallback;
    private AdvertiseSettings m_adSettings;
    private AdvertiseData m_adData;
    private BluetoothDevice m_centralDevice;
    private int m_centralMTU;
    private BluetoothGattCharacteristic m_writeCharacteristic;
    private BluetoothGattCharacteristic m_readCharacteristic;
    public final List<byte[]> PeripheralMessageQueue = new LinkedList<>();
    private List<PeripheralWriteRequestData> m_writeList = new ArrayList<>();
    //endregion

    //region COMMON FUNCTIONS
    public boolean getIsInit() {
        return m_isInit;
    }

    private static BLEHandler ourInstance = new BLEHandler();

    public static BLEHandler getInstance() {
        return ourInstance;
    }

    private BLEHandler() {
        m_scanHandler = new Handler();
    }

    public void setIsCentral(boolean isCentral) {
        this.isCentral = isCentral;
    }

    public boolean isCentral() {
        return isCentral;
    }

    public void setCurrentActivity(Activity activity) {
        m_currentActivity = activity;
    }

    public boolean isScanningOrAdvertising() {
        return m_isScanningOrAdvertising;
    }

    private boolean initBluetoothManager() {
        if (m_bluetoothManager == null) {
            m_bluetoothManager = (BluetoothManager) m_currentActivity.getSystemService(Context.BLUETOOTH_SERVICE);
            if (m_bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        return true;
    }

    public void setup() {
        m_isInit = true;
        initBluetoothManager();
        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (m_bluetoothAdapter != null && m_bluetoothAdapter.isEnabled()) {
            if (isCentral()) {
                initCentral();
                //m_senderThread = new SenderThread();
                //m_senderThread.start();
            } else {
                if (!m_bluetoothAdapter.isMultipleAdvertisementSupported()) {
                    Log.d(TAG, "setup: isMultipleAdvertisementSupported : false");
                    broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "peripheral mode is not supported by this device");
                    broadcastStatus(BLE_CONNECTION_NOT_SUPPORT_ACTION);
                    return;
                }

                initPeripheral();
            }

        } else {
            Log.d(TAG, "setupNetwork: bluetooth is not enabled");
            // broadcast message
            broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "bluetooth is not enabled");
        }
    }

    public void disconnect() {
        if (isCentral()) {
            if (m_isScanningOrAdvertising) {
                stopScan();
            }

            if (m_peripheralDevices != null) {
                Enumeration<PeripheralInfo> values = m_peripheralDevices.elements();
                while (values.hasMoreElements()) {
                    PeripheralInfo info = values.nextElement();

                    if (info.m_bluetoothGatt != null) {
                        if (isCentral() && info.m_readCharacteristic != null) {
                            // unsubscribe
                            info.m_bluetoothGatt.setCharacteristicNotification(info.m_readCharacteristic, false);

                            BluetoothGattDescriptor descriptor = info.m_readCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                            info.m_bluetoothGatt.writeDescriptor(descriptor);
                        }
                        info.m_bluetoothGatt.disconnect();
                        info.m_bluetoothGatt.close();
                        info.m_bluetoothGatt = null;
                    }
                }
                m_peripheralDevices.clear();
                m_peripheralDevices = null;
            }
            CentralMessageSendQueue.clear();

            /*
            if (m_senderThread != null){
                m_senderThread.interrupt();
                m_senderThread = null; // ???
            }*/
        } else {
            setIsAdvertise(false);
            if (m_bluetoothGattServer != null) {
                if (m_centralDevice != null) {
                    m_bluetoothGattServer.cancelConnection(m_centralDevice);
                    m_centralDevice = null;
                }
                m_bluetoothGattServer.close();
                m_bluetoothGattServer = null;
            }
            PeripheralMessageQueue.clear();
        }

        m_isInit = false;
    }

    private void broadcastStatus(String action) {
        Intent i = new Intent(action);
        m_currentActivity.sendBroadcast(i);
    }

    private void broadcastStatus(String action, String msg) {
        Intent i = new Intent(action);
        i.putExtra(BLE_EXTRA_DATA, msg);
        m_currentActivity.sendBroadcast(i);
    }

    private void broadcastReceiveDatAction(String deviceAddress, byte[] value, long receiveTime) {
        Intent i = new Intent(BLE_RECEIVED_DATA_ACTION);
        i.putExtra(BLE_EXTRA_DATA_RECEIVE_TIME, receiveTime);
        i.putExtra(BLE_EXTRA_DATA, value);
        i.putExtra(BLE_EXTRA_DATA_ADDRESS, deviceAddress);
        m_currentActivity.sendBroadcast(i);
    }

    public int getConnectionCount() {
        if (isCentral()) {
            return m_peripheralDevices == null ? 0 : m_peripheralDevices.size();
        } else {
            return m_centralDevice == null ? 0 : 1;
        }
    }

    private List<byte[]> makeMsg(byte[] message, int capacity) {
        Log.d(TAG, "makeMsg: msg length = " + message.length + ", capacity = " + capacity);
        int limitation = capacity - SYSTEM_RESERVED_MTU - 2;

        List<byte[]> msgArray = new ArrayList<>();
        if (message.length < limitation) {
            msgArray.add(packMsg(message, true, true));
        } else {
            int index = 0;
            boolean isCompleted = false;

            while (!isCompleted) {
                int amountToSend = message.length - index;

                if (amountToSend >  limitation) {
                    amountToSend = limitation;
                    isCompleted = false;
                } else {
                    isCompleted = true;
                }

                byte[] chunk = new byte[amountToSend];
                System.arraycopy(message, index, chunk, 0, amountToSend);
                msgArray.add(packMsg(chunk, (index == 0), isCompleted));

                index += amountToSend;
            }
        }

        return msgArray;
    }

    private void processMsg(byte[] message, String deviceAddress, long time) {
        byte newMsg =  message[0];
        byte isCompleted =  message[1];

        byte[] data = new byte[message.length - 2];
        System.arraycopy(message, 2, data, 0, message.length-2);

        if (newMsg != 0) {
            if (isCompleted != 0) {
                broadcastReceiveDatAction(deviceAddress, data, time);
            } else {
                MessageData msgData = getMessageData(deviceAddress);
                if (msgData == null) {
                    msgData = new MessageData(deviceAddress);
                } else {
                    msgData.clearData();
                }

                msgData.addData(data);

                m_recMsgArray.add(msgData);
            }
        } else {
            MessageData msgData = getMessageData(deviceAddress);
            if (msgData != null) {
                msgData.addData(data);
                if (isCompleted != 0) {
                    broadcastReceiveDatAction(deviceAddress, msgData.getFullData(), time);
                    msgData.clearData();
                    m_recMsgArray.remove(msgData);
                }
            }
        }
    }

    private MessageData getMessageData(String deivceAddress) {
        MessageData data = null;
        for (MessageData d : m_recMsgArray) {
            if (d.getDeviceAddress().equalsIgnoreCase(deivceAddress)) {
                data = d;
                break;
            }
        }

        return data;
    }

    private byte[] packMsg(byte[] message, boolean isNew, boolean isCompleted) {
        byte isNewb = (byte) (isNew ? 1 :0);
        byte isCompletedb = (byte) (isCompleted ? 1 :0);

        byte[] result = new byte[message.length + 2];

        result[0] = isNewb;
        result[1] = isCompletedb;
        System.arraycopy(message, 0, result, 2, message.length);

        return result;
    }
    //endregion

    //region CENTRAL
    /***********************************************************************/
    /**                          CENTRAL FUNCTIONS                        **/
    /***********************************************************************/
    private void initCentral() {
        m_peripheralDevices = new Hashtable<>();
        m_LEScanner = m_bluetoothAdapter.getBluetoothLeScanner();
        m_scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        m_filters = new ArrayList<>();
        initScanCallback();
        initGattCallback();
    }

    public void startScan() {
        if (m_bluetoothAdapter != null && m_bluetoothAdapter.isEnabled()) {
            scanLeDevice(true);
        }
    }

    public void stopScan() {
        if (m_bluetoothAdapter != null && m_bluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
            m_scanHandler.removeCallbacks(m_scanRunnable);
        }
    }

    private Runnable m_scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (m_isScanningOrAdvertising) {
                m_isScanningOrAdvertising = false;
                m_LEScanner.stopScan(m_scanCallback);
                broadcastStatus(BLE_CONNECTION_AUTO_STOP_SCAN_ACTION);
            }
        }
    };

    private void scanLeDevice(final boolean enable) {
        if (enable && !m_isScanningOrAdvertising) {
            // Stops scanning after a pre-defined scan period.
            m_scanHandler.postDelayed(m_scanRunnable, SCAN_PERIOD);

            // android.os.Build.VERSION.SDK_INT> 21
            m_isScanningOrAdvertising = true;
            m_LEScanner.startScan(m_filters, m_scanSettings, m_scanCallback);
        } else {
            m_isScanningOrAdvertising = false;
            m_LEScanner.stopScan(m_scanCallback);
        }
    }

    private void initScanCallback() {
        m_scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                //Log.i("callbackType", String.valueOf(callbackType));
                //Log.i("result", result.toString());

                BluetoothDevice device = result.getDevice();

                if (m_peripheralDevices.containsKey(device.getAddress())) {
                    return;
                }

                ScanRecord scanRecord = result.getScanRecord();
                try {
                    List<ParcelUuid> serviceUUIDs = scanRecord != null ? scanRecord.getServiceUuids() : null;
                    if (serviceUUIDs == null) {
                        Log.d(TAG, "onScanResult: no services found!");
                        return;
                    }

                    boolean isFound = false;
                    for (ParcelUuid uuid : serviceUUIDs) {
                        Log.d(TAG, "UUUUID: " + uuid.toString());
                        if (uuid.toString().equalsIgnoreCase(TRANSFER_SERVICE_UUID)) {
                            isFound = true;
                            break;
                        }
                    }

                    if (isFound) {
                        Log.d(TAG, "onScanResult: new device found : " + device.getName() + " with rssi : " + result.getRssi());
                        PeripheralInfo info = new PeripheralInfo();
                        info.m_bluetoothGatt = null;
                        info.m_name = device.getName();
                        info.m_readCharacteristic = null;
                        info.m_writeCharacteristic = null;
                        info.m_mtu = DEFAULT_MTU;
                        m_peripheralDevices.put(device.getAddress(), info);
                        broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "device found : " + device.getName() + " with rssi : " + result.getRssi());

                        // stop scan
                        //m_isScanningOrAdvertising = false;
                        //m_LEScanner.stopScan(m_scanCallback);
                        //broadcastStatus(BLE_CONNECTION_AUTO_STOP_SCAN_ACTION);

                        // connect to peripheral
                        broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "connecting to : " + device.getName());
                        connectToPeripheral(device, info);
                    }
                } catch (NullPointerException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult sr : results) {
                    Log.i("ScanResult - Results", sr.toString());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("Scan Failed", "Error Code: " + errorCode);
                m_isScanningOrAdvertising = false;
                m_LEScanner.stopScan(m_scanCallback);
                broadcastStatus(BLE_CONNECTION_AUTO_STOP_SCAN_ACTION);
                broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "scan failed : " + errorCode);
            }
        };
    }

    private void connectToPeripheral(BluetoothDevice device, PeripheralInfo info) {
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.M) {
            info.m_bluetoothGatt = device.connectGatt(m_currentActivity, false, m_gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            try {
                Method m = device.getClass().getDeclaredMethod("connectGatt", Context.class, boolean.class, BluetoothGattCallback.class, int.class);
                int transport = device.getClass().getDeclaredField("TRANSPORT_LE").getInt(null);     // LE = 2, BREDR = 1, AUTO = 0
                info.m_bluetoothGatt = (BluetoothGatt) m.invoke(device, m_currentActivity, false, m_gattCallback, transport);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        m_peripheralDevices.put(device.getAddress(), info);
    }

    private void initGattCallback() {
        m_gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "connected to peripheral " + gatt.getDevice().getName() + "\nstart to discover services");
                    Log.d(TAG, "Connected to GATT server.");
                    // Attempts to discover services after successful connection.
                    Log.d(TAG, "Attempting to start service discovery:" + gatt.discoverServices());

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server : " + gatt.getDevice().getName());
                    m_peripheralDevices.remove(gatt.getDevice().getAddress());
                    broadcastStatus(BLE_GATT_DISCONNECTED_ACTION);
                    gatt.disconnect();
                    gatt.close();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onServicesDiscovered: status " + status);
                    broadcastStatus(BLE_GATT_SERVICES_DISCOVERED_ACTION);
                    boolean isReadCharFound = false;
                    boolean isWriteCharFound = false;
                    PeripheralInfo info = m_peripheralDevices.get(gatt.getDevice().getAddress());
                    List<BluetoothGattService> services = gatt.getServices();
                    for (BluetoothGattService service : services) {
                        List<BluetoothGattCharacteristic> chars =  service.getCharacteristics();
                        for (BluetoothGattCharacteristic ch : chars) {
                            if (ch.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID)) {
                                Log.d(TAG, "onServicesDiscovered: TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID found! ");
                                Log.d(TAG, "onServicesDiscovered: characteristic properties : " + ch.getProperties());

                                if ((ch.getProperties() &
                                        BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                                    Log.d(TAG, "onServicesDiscovered: cannot read the characteristic" );
                                }

                                isReadCharFound = true;
                                info.m_readCharacteristic = ch;
                                Log.d(TAG, "onServicesDiscovered: setNotification : " + gatt.setCharacteristicNotification(ch, true));
                                BluetoothGattDescriptor descriptor = ch.getDescriptor(
                                        UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                Log.d(TAG, "onServicesDiscovered: setDescriptor : " + gatt.writeDescriptor(descriptor));
                            } else if (ch.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID)) {
                                Log.d(TAG, "onServicesDiscovered: TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID found!");
                                Log.d(TAG, "onServicesDiscovered: characteristic properties : " + ch.getProperties());
                                isWriteCharFound = true;
                                info.m_writeCharacteristic = ch;
                            }
                        }
                    }
                    if (isReadCharFound && isWriteCharFound) {
                        m_peripheralDevices.put(gatt.getDevice().getAddress(), info);
                        broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "required services are found \n start to subscribe notification");
                    } else {
                        m_peripheralDevices.remove(gatt.getDevice().getAddress());
                        gatt.disconnect();
                        gatt.close();
                        broadcastStatus(BLE_GATT_DISCONNECTED_ACTION);
                    }
                } else {
                    Log.d(TAG, "onServicesDiscovered received: " + status);
                    broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "cannot discovery required services on " + gatt.getDevice().getName());

                    m_peripheralDevices.remove(gatt.getDevice().getAddress());
                    gatt.disconnect();
                    gatt.close();
                    broadcastStatus(BLE_GATT_DISCONNECTED_ACTION);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.d(TAG, "onCharacteristicRead: " + characteristic.getUuid().toString() + " status : " + status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                Log.d(TAG, "onCharacteristicWrite: " + characteristic.getUuid().toString() + " device = " + gatt.getDevice().getAddress() + " status = " + status);
                try {
                    //Message.PingMessage msg = Message.PingMessage.parseFrom(characteristic.getValue());
                    //Log.d(TAG, "onCharacteristicWrite: value = " + msg.getToken());

                    //gatt.executeReliableWrite();

                    synchronized (CentralMessageSendQueue) {
                        CentralMessageSendQueue.remove(0);
                        if (CentralMessageSendQueue.size() != 0) {
                            sendDataToPeripherals();
                        }
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString() + " , size : " + characteristic.getValue().length);

                if (characteristic.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID)) {
                    processMsg(characteristic.getValue(), gatt.getDevice().getAddress(), System.nanoTime());
                }
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
                Log.d(TAG, "onReliableWriteCompleted: status : " + status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d(TAG, "onDescriptorWrite: " + descriptor.getUuid().toString() + " status : " + status);
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "cannot subscribe notification on " + gatt.getDevice().getName());

                    m_peripheralDevices.remove(gatt.getDevice().getAddress());
                    gatt.disconnect();
                    gatt.close();
                    broadcastStatus(BLE_GATT_DISCONNECTED_ACTION);
                } else {
                    gatt.requestMtu(MAX_MTU); // request max MTU once all stuff have been done
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                //status=GATT_SUCCESS(0) if the MTU has been changed successfully
                Log.d(TAG, "onMtuChanged: peripheral name = " + gatt.getDevice().getName() + ", MTU = " + mtu + ", status = " + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    PeripheralInfo info = m_peripheralDevices.get(gatt.getDevice().getAddress());
                    if (info != null) {
                        info.m_mtu = mtu;
                        m_peripheralDevices.put(gatt.getDevice().getAddress(), info);
                    }
                    broadcastStatus(BLE_GATT_CONNECTED_ACTION);
                }
            }
        };
    }

    public boolean sendDataToAllPeripherals(byte[] message) {
        boolean rt = false;
        if (m_peripheralDevices != null) {
            Enumeration<PeripheralInfo> values = m_peripheralDevices.elements();
            while (values.hasMoreElements()) {
                PeripheralInfo info = values.nextElement();

                if (info.m_bluetoothGatt != null && info.m_writeCharacteristic != null) {
                    //Log.d(TAG, "sendDataToAllPeripherals: initReliableWrite : " + info.m_bluetoothGatt.beginReliableWrite());
                    List<byte[]> msgs = makeMsg(message, MAX_MTU);
                    synchronized (CentralMessageSendQueue) {
                        boolean shouldExecute = CentralMessageSendQueue.size() == 0;
                        for (byte[] msg : msgs) {
                            CentralSendMessageInfo msgInfo = new CentralSendMessageInfo();
                            msgInfo.m_bluetoothGatt = info.m_bluetoothGatt;
                            msgInfo.m_characteristic = info.m_writeCharacteristic;
                            msgInfo.m_value = msg;
                            CentralMessageSendQueue.add(msgInfo);
                        }

                        if (shouldExecute) {
                            rt = sendDataToPeripherals();
                        }
                    }
                }
            }
        }

        return rt;
    }

    public boolean sendDataToPeripheral(String address, byte[] message) {
        PeripheralInfo info = m_peripheralDevices.get(address);
        boolean rt = false;
        if (info != null) {
            if (info.m_bluetoothGatt != null && info.m_writeCharacteristic != null) {
                List<byte[]> msgs = makeMsg(message, MAX_MTU);
                synchronized (CentralMessageSendQueue) {
                    boolean shouldExecute = CentralMessageSendQueue.size() == 0;

                    for (byte[] msg : msgs) {
                        CentralSendMessageInfo msgInfo = new CentralSendMessageInfo();
                        msgInfo.m_bluetoothGatt = info.m_bluetoothGatt;
                        msgInfo.m_characteristic = info.m_writeCharacteristic;
                        msgInfo.m_value = msg;
                        CentralMessageSendQueue.add(msgInfo);
                    }

                    if (shouldExecute) {
                        rt = sendDataToPeripherals();
                    }
                }
            }
        }

        return rt;
    }

    private boolean sendDataToPeripherals() {
        boolean rt = false;
        synchronized (CentralMessageSendQueue) {
            if (CentralMessageSendQueue.size() != 0) {
                CentralSendMessageInfo msgInfo = CentralMessageSendQueue.get(0);
                if (msgInfo.m_bluetoothGatt != null && msgInfo.m_characteristic != null) {
                    msgInfo.m_characteristic.setValue(msgInfo.m_value);
                    rt = msgInfo.m_bluetoothGatt.writeCharacteristic(msgInfo.m_characteristic);
                    Log.d(TAG, "sendDataToPeripherals: byteMsg size :" + msgInfo.m_value.length);
                } else {
                    Log.d(TAG, "sendDataToPeripherals: failed, CentralMessageSendQueue.size() = " + CentralMessageSendQueue.size() +
                            "msgInfo.m_bluetoothGatt = " + (msgInfo.m_bluetoothGatt != null) + "msgInfo.m_characteristic = " + (msgInfo.m_characteristic != null));
                }
            }
        }

        return rt;
    }

    public int getPeripheralMTU(String address) {
        PeripheralInfo info = m_peripheralDevices.get(address);
        if (info != null) {
            return info.m_mtu;
        } else {
            return -1;
        }
    }

    //endregion

    //region PERIPHERAL
    /***********************************************************************/
    /**                          PERIPHERAL FUNCTIONS                     **/
    /***********************************************************************/

    private void initPeripheral() {
        m_advertiser = m_bluetoothAdapter.getBluetoothLeAdvertiser();
        if (m_advertiser == null) {
            Log.d(TAG, "setup: can not get ble advertiser!!!");
            return;
        }
        m_adSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode( AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY )
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        ParcelUuid pUuid = new ParcelUuid( UUID.fromString( TRANSFER_SERVICE_UUID ) );
        m_adData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(pUuid)
                .build();

        initAdCallback();
        initGattServerCallback();
        initGattServer();
    }

    private void initGattServerCallback() {
        m_gattServerCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Log.d(TAG, "gatt server connection state changed, new state " + newState);
                super.onConnectionStateChange(device, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    m_centralDevice = device;
                    broadcastStatus(BLE_GATT_CONNECTED_ACTION);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from central : " + device.getName());
                    if (device.equals(m_centralDevice)) {
                        m_centralDevice = null;
                        broadcastStatus(BLE_GATT_DISCONNECTED_ACTION);
                    }
                }
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                Log.d(TAG, "service id = " + service.getUuid() + ", added with status = " + status);
                super.onServiceAdded(status, service);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "received a read request from " + device.getName() + " to characteristic " + characteristic.getUuid());
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.d(TAG, "onCharacteristicWriteRequest requestId = " + requestId + " " + "received a write request from " + device + " to characteristic " + characteristic.getUuid() + " valueLength = " + value.length + " offset = " + offset + " preparedWrite = " + preparedWrite + " responseNeeded = " + responseNeeded);
                //super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                if (m_bluetoothGattServer != null && responseNeeded) {
                    m_bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                if (characteristic.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID)) {
                    if (preparedWrite) {
                        addWriteItemByteBuffer(device.getAddress(), characteristic.getUuid().toString(), value, true);
                    } else {
                        processMsg(characteristic.getValue(), device.getAddress(), System.nanoTime());
                    }
                }
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                Log.d(TAG, "Our gatt server descriptor was read.");
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.d(TAG, "Our gatt server descriptor was written.");
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                m_bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                Log.d(TAG, "gatt server on execute write device = " + device + " requestId = " + requestId + " execute = " + execute);
                super.onExecuteWrite(device, requestId, execute);
                if (m_bluetoothGattServer != null) {
                    m_bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{});
                }

                executeWriteRequest(device.getAddress(), execute);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                super.onNotificationSent(device, status);
                Log.d(TAG, "onNotificationSent to " + device.getName() + " status : " + status);
                synchronized (PeripheralMessageQueue) {
                    if (status == BluetoothGatt.GATT_SUCCESS && PeripheralMessageQueue.size() != 0) {
                        PeripheralMessageQueue.remove(0);
                    }

                    if (PeripheralMessageQueue.size() != 0) {
                        sendDataToCentral();
                    }
                }
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
                Log.d(TAG, "onMtuChanged: device : " + device.getName() + ", mtu : " + mtu);
                if (device.equals(m_centralDevice)) {
                    m_centralMTU = mtu;
                }
            }
        };
    }

    private void initAdCallback() {
        m_advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.d(TAG, "Start advertising...");
                broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "Start advertising...");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
                m_isScanningOrAdvertising = false;
                broadcastStatus(BLE_CONNECTION_UPDATE_ACTION, "Start Advertising failed : " + errorCode);
                broadcastStatus(BLE_CONNECTION_AUTO_STOP_SCAN_ACTION);
            }
        };
    }

    private void initGattServer() {
        m_bluetoothGattServer = m_bluetoothManager.openGattServer(m_currentActivity, m_gattServerCallback);
        BluetoothGattService service = new BluetoothGattService(UUID.fromString(TRANSFER_SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        m_readCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE );

        m_writeCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ );
        BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        m_writeCharacteristic.addDescriptor(gD);

        if (!service.addCharacteristic(m_readCharacteristic)) {
            Log.d(TAG, "initGattServer: cannot add receive characteristic!!!");
        }
        if (!service.addCharacteristic(m_writeCharacteristic)) {
            Log.d(TAG, "initGattServer: cannot add send characteristic!!!");
        }

        m_bluetoothGattServer.addService(service);
        Log.d(TAG, "initGattServer: done!");
    }

    public void setIsAdvertise(boolean enable) {
        if (enable) {
            m_isScanningOrAdvertising = true;
            m_advertiser.startAdvertising(m_adSettings, m_adData, m_advertisingCallback);
        } else {
            m_isScanningOrAdvertising = false;
            if (m_advertiser != null) {
                m_advertiser.stopAdvertising(m_advertisingCallback);
            }
        }
    }

    public boolean sendDataToCentral(byte[] message) {
        boolean rt = false;
        if (m_writeCharacteristic != null) {

            List<byte[]> msgs = makeMsg(message, m_centralMTU);
            synchronized (PeripheralMessageQueue) {
                boolean shouldExecute = PeripheralMessageQueue.size() == 0;
                for (byte[] msg : msgs) {
                    PeripheralMessageQueue.add(msg);
                }

                if (shouldExecute) {
                    rt = sendDataToCentral();
                }
            }

        }

        return rt;
    }
    
    private boolean sendDataToCentral() {
        boolean rt = false;
        synchronized (PeripheralMessageQueue) {
            if (PeripheralMessageQueue.size() != 0 && m_bluetoothGattServer != null && m_centralDevice != null) {
                byte[] msg = PeripheralMessageQueue.get(0);
                m_writeCharacteristic.setValue(msg);
                rt = m_bluetoothGattServer.notifyCharacteristicChanged(m_centralDevice, m_writeCharacteristic, false);
                Log.d(TAG, "sendDataToCentral: byteMsg size :" + msg.length);
            } else {
                Log.d(TAG, "sendDataToCentral: failed, PeripheralMessageQueue.size() = " + PeripheralMessageQueue.size() +
                        "m_bluetoothGattServer = " + (m_bluetoothGattServer != null) + "m_centralDevice = " + (m_centralDevice != null));
            }
        }
        return rt;
    }

    private void executeWriteRequest(String deviceAddress, boolean execute){

        for(int i=m_writeList.size() - 1; i >= 0;i--){
            PeripheralWriteRequestData storage  = m_writeList.get(i);
            if(storage != null && storage.getDeviceAddress().equalsIgnoreCase(deviceAddress)){
                if(execute){//if its not for executing, its then for cancelling it
                    if(storage.isCharacter()){
                        processMsg(storage.getFullData(), storage.getDeviceAddress(), System.nanoTime());
                    }
                }

                m_writeList.remove(storage);
                //we are done with this item now.
                storage.clearData();
            }
        }
    }

    private void addWriteItemByteBuffer(String deviceAddress, String uuid,byte[] buffer, boolean isCharacter){
        PeripheralWriteRequestData  data = getWriteItem(deviceAddress,uuid);
        if(data != null){
            data.addData(buffer);
        }else{
            PeripheralWriteRequestData newItem = new PeripheralWriteRequestData(deviceAddress,uuid,isCharacter);
            newItem.addData(buffer);
            m_writeList.add(newItem);
        }
    }


    private PeripheralWriteRequestData getWriteItem(String deviceAddress, String uuid) {
        PeripheralWriteRequestData ret = null;
        for (PeripheralWriteRequestData data : m_writeList){
            if(data != null && data.getUUID().equalsIgnoreCase(uuid) && data.getDeviceAddress().equalsIgnoreCase(deviceAddress)){
                ret = data;
                break;
            }
        }
        return ret;
    }
    //endregion
}
