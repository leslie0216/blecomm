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
        public int m_sendIndex;
        public int m_sendCount;
    }

    private class PeripheralInfo {
        public BluetoothGatt m_bluetoothGatt;
        public BluetoothGattCharacteristic m_writeCharacteristic;
        public BluetoothGattCharacteristic m_readCharacteristic;
        public String m_name;
        public int m_mtu;
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

    //endregion

    //region COMMON VARS
    private Activity m_currentActivity;
    private BluetoothManager m_bluetoothManager;
    private BluetoothAdapter m_bluetoothAdapter;
    private boolean isCentral;
    private boolean m_isScanningOrAdvertising;
    private boolean m_isInit = false;
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
    public List<CentralSendMessageInfo> CentralMessageSendQueue = new LinkedList<>();
    //endregion

    //region PERIPHERAL VARS
    private BluetoothGattServer m_bluetoothGattServer;
    private BluetoothGattServerCallback m_gattServerCallback;
    private BluetoothLeAdvertiser m_advertiser;
    private AdvertiseCallback m_advertisingCallback;
    private AdvertiseSettings m_adSettings;
    private AdvertiseData m_adData;
    private BluetoothDevice m_centralDevice;
    private BluetoothGattCharacteristic m_writeCharacteristic;
    private BluetoothGattCharacteristic m_readCharacteristic;
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

    public int getConnectionCount() {
        if (isCentral()) {
            return m_peripheralDevices.size();
        } else {
            return m_centralDevice == null ? 0 : 1;
        }
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
                    Log.i(TAG, "Disconnected from GATT server : " + gatt.getDevice().getName());
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
                Log.d(TAG, "onCharacteristicWrite: " + characteristic.getUuid().toString() + " status=" + status);
                try {
                    //Message.PingMessage msg = Message.PingMessage.parseFrom(characteristic.getValue());
                    //Log.d(TAG, "onCharacteristicWrite: value = " + msg.getToken());

                    //gatt.executeReliableWrite();

                    if (status == BluetoothGatt.GATT_SUCCESS && CentralMessageSendQueue.size() != 0) {
                        CentralMessageSendQueue.remove(0);
                    }

                    if (CentralMessageSendQueue.size() != 0) {
                        CentralSendMessageInfo msgInfo = CentralMessageSendQueue.get(0);
                        msgInfo.m_characteristic.setValue(msgInfo.m_value);
                        msgInfo.m_bluetoothGatt.writeCharacteristic(msgInfo.m_characteristic);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString() + " , size : " + characteristic.getValue().length);

                if (characteristic.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_PERIPHERAL_UUID)) {
                    long receiveTime = System.nanoTime();

                    Intent i = new Intent(BLE_RECEIVED_DATA_ACTION);
                    i.putExtra(BLE_EXTRA_DATA_RECEIVE_TIME, receiveTime);
                    i.putExtra(BLE_EXTRA_DATA, characteristic.getValue());
                    i.putExtra(BLE_EXTRA_DATA_ADDRESS, gatt.getDevice().getAddress());
                    m_currentActivity.sendBroadcast(i);
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

    public boolean sendDataToAllPeripherals(byte[] msg) {
        boolean rt = false;
        if (m_peripheralDevices != null) {
            Enumeration<PeripheralInfo> values = m_peripheralDevices.elements();
            while (values.hasMoreElements()) {
                PeripheralInfo info = values.nextElement();

                if (info.m_writeCharacteristic != null) {
                    //Log.d(TAG, "sendDataToAllPeripherals: initReliableWrite : " + info.m_bluetoothGatt.beginReliableWrite());
                    CentralSendMessageInfo msgInfo = new CentralSendMessageInfo();
                    msgInfo.m_bluetoothGatt = info.m_bluetoothGatt;
                    msgInfo.m_characteristic = info.m_writeCharacteristic;
                    msgInfo.m_value = msg;
                    msgInfo.m_sendCount = 1;
                    msgInfo.m_sendIndex = 1;
                    CentralMessageSendQueue.add(msgInfo);

                    if (CentralMessageSendQueue.size() == 1) {
                        info.m_writeCharacteristic.setValue(msg);
                        rt = info.m_bluetoothGatt.writeCharacteristic(info.m_writeCharacteristic);
                    }
                } else {
                    rt = false;
                }
            }
        }
        Log.d(TAG, "sendDataToAllPeripheral: byteMsg size : " + msg.length + ", result : " + rt);

        return rt;
    }

    public boolean sendDataToPeripheral(String address, byte[] msg) {
        PeripheralInfo info = m_peripheralDevices.get(address);
        boolean rt = false;
        if (info != null) {
            if (info.m_writeCharacteristic != null) {
                CentralSendMessageInfo msgInfo = new CentralSendMessageInfo();
                msgInfo.m_bluetoothGatt = info.m_bluetoothGatt;
                msgInfo.m_characteristic = info.m_writeCharacteristic;
                msgInfo.m_value = msg;
                msgInfo.m_sendCount = 1;
                msgInfo.m_sendIndex = 1;
                CentralMessageSendQueue.add(msgInfo);

                if (CentralMessageSendQueue.size() == 1) {
                    info.m_writeCharacteristic.setValue(msg);
                    rt = info.m_bluetoothGatt.writeCharacteristic(info.m_writeCharacteristic);
                }
            } else {
                rt =  false;
            }
            Log.d(TAG, "sendDataToPeripheral: byteMsg size : " + msg.length + ", to : " + info.m_name +  ", result : " + rt);
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
                long receiveTime = System.nanoTime();
                Log.d(TAG, "received a write request from " + device.getName() + " to characteristic " + characteristic.getUuid());
                //super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                m_bluetoothGattServer.sendResponse(device, requestId,  BluetoothGatt.GATT_SUCCESS, offset, value);

                if (characteristic.getUuid().toString().equalsIgnoreCase(TRANSFER_CHARACTERISTIC_MSG_FROM_CENTRAL_UUID)) {
                    Intent i = new Intent(BLE_RECEIVED_DATA_ACTION);
                    i.putExtra(BLE_EXTRA_DATA_RECEIVE_TIME, receiveTime);
                    i.putExtra(BLE_EXTRA_DATA, value);
                    m_currentActivity.sendBroadcast(i);
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
                Log.d(TAG, "gatt server on execute write device = " + device + " requestId = " + " execute = " + execute);
                super.onExecuteWrite(device, requestId, execute);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                super.onNotificationSent(device, status);
                Log.d(TAG, "onNotificationSent to " + device.getName() + " status : " + status);
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
                Log.d(TAG, "onMtuChanged: device : " + device.getName() + ", mtu : " + mtu);
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

    public boolean sendDataToCentral(byte[] msg) {
        boolean rt;
        m_writeCharacteristic.setValue(msg);
        rt = !isCentral() &&
                m_centralDevice != null &&
                m_bluetoothGattServer != null &&
                m_bluetoothGattServer.notifyCharacteristicChanged(m_centralDevice, m_writeCharacteristic, false);
        Log.d(TAG, "sendDataToCentral: byteMsg size : " + msg.length + ", result : " + rt);
        return rt;
    }
    //endregion
}
