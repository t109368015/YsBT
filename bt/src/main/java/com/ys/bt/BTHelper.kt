package com.ys.bt

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import java.util.*

class BTHelper(private var context: Context, var nCallBack: BTCallBack) {
    private val scan = BTScanDevice(this)
    private val service = BTServiceControl(context, this)
    private var TAG = BTHelper::class.java.simpleName
    var bluetoothManager: BluetoothManager? = null
    var bluetoothAdapter: BluetoothAdapter? = null
    var RxChannel = ""
    var TxChannel = ""
    var RxData = ""
    var isBTOpen = false

    enum class DataType {
        Hex, String, ByteArray
    }

    init {
        initialize()
        registerBTReceiver()
    }

    private var receiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val status = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                nCallBack.onStatusChange(status)
                when (status) {
                    BluetoothAdapter.STATE_OFF -> {
                        isBTOpen = false
                        Log.d("YsBT: $TAG", "STATE_OFF")
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> Log.d("YsBT: $TAG", "STATE_TURNING_OFF")
                    BluetoothAdapter.STATE_ON -> {
                        isBTOpen = true
                        Log.d("YsBT: $TAG", "STATE_ON")
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> Log.d("YsBT: $TAG", "STATE_TURNING_ON")
                }
            }
        }
    }

    /**
     * Initializes the Bluetooth functionality.
     * Checks and requests permission, initializes BluetoothManager and BluetoothAdapter,
     * and checks if Bluetooth is enabled on the device.
     * Returns true if initialization is successful, false otherwise.
     */
    fun initialize(): Boolean {
        if (!checkAndRequestPermission()) return false
        if (bluetoothManager == null) bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter ?: run {
            Log.d("YsBT: $TAG", "Unable to initialize BluetoothManager.")
            return false
        }

        return bluetoothAdapter?.let {
            isBTOpen = it.isEnabled
            true
        } ?: run {
            Log.d("YsBT: $TAG", "Unable to obtain a BluetoothAdapter.")
            false
        }
    }

    /**
     * Registers a BroadcastReceiver to listen for Bluetooth-related events.
     * Adds actions for Bluetooth bond state changes and Bluetooth adapter state changes to the filter.
     */
    fun registerBTReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    /**
     * Initiates a device scan if Bluetooth is enabled.
     * Starts scanning for nearby Bluetooth devices.
     */
    fun scanDevice() {
        if (!checkBluetoothStatus()) return
        if (scan.isScanning) scan.stopScanDevice()
        scan.startScanDevice()
    }

    /**
     * Stops the ongoing device scan.
     * Stops scanning for nearby Bluetooth devices.
     */
    fun stopScanDevice() {
        if (!checkBluetoothStatus()) return
        scan.stopScanDevice()
    }

    /**
     * Checks if a device scan is currently in progress.
     * Returns true if a scan is in progress, false otherwise.
     */
    fun isScanning() = scan.isScanning

    /**
     * Retrieves the list of scanned Bluetooth devices.
     * Returns the list of scanned Bluetooth devices.
     */
    fun getScanDeviceList(): HashSet<BluetoothDevice> = if (!checkBluetoothStatus()) HashSet() else scan.scanDeviceList

    /**
     * Initiates a connection to a Bluetooth device with the given address.
     * The connection is delayed by the specified duration.
     * @param address The address of the Bluetooth device to connect to.
     */
    fun connect(address: String) = service.connect(address)

    /**
     * Disconnects from the currently connected Bluetooth device.
     */
    fun disConnect() = service.close()

    /**
     * Checks if a connection to a Bluetooth device is established.
     * Returns true if a connection is established, false otherwise.
     */
    fun isConnect() = service.isConnect

    /**
     * Displays the GATT services on the connected Bluetooth device.
     * @param gattServices The list of BluetoothGattService objects representing the services.
     */
    fun getGattServices(gattServices: List<BluetoothGattService>?) = service.displayGattServices(gattServices)

    /**
     * Retrieves the list of services supported by the connected Bluetooth device.
     * Returns the list of supported services.
     */
    fun getServiceList() = service.getServiceList()

    /**
     * If you need to specify the "tx" UUID, you need to know the UUID of its service and characteristic in advance.
     */
    fun setTx(tx: String) { TxChannel = tx }

    /**
     * If you need to retrieve a specific "rx", you need to subscribe to its service and characteristic UUID in advance.
     * This library automatically unsubscribes the old channels when subscribing to new channels, so the setRx function has no practical meaning.
     * It is recommended to directly use the descriptorChannel function.
     */
    fun setRx(rx: String) { RxChannel = rx }

    /**
     * Subscribes to a known service by providing the service UUID and characteristic UUID.
     * If device information is known, a subscription is initiated for the specified service.
     */
    fun descriptorChannel(serviceUUID: UUID, characteristicUUID: UUID) {
        Log.d("YsBT: $TAG", "Set RxChannel: $characteristicUUID")
        if (RxChannel.isNotEmpty()) service.descriptorChannel(serviceUUID, UUID.nameUUIDFromBytes(RxChannel.toByteArray()), false) //查看看 isEnable是什麼? 可以取消訂閱嗎?
        RxChannel = characteristicUUID.toString()
        service.descriptorChannel(serviceUUID, characteristicUUID, true)
    }

    /**
     * Subscribes to a characteristic by directly using the characteristic value obtained
     * through iterating device services when the device information is unknown.
     */
    fun descriptorChannelByCharacteristic(characteristic: BluetoothGattCharacteristic) {
        Log.d("YsBT: $TAG", "Set RxChannel: ${characteristic.uuid}")
        if (RxChannel.isNotEmpty()) service.descriptorChannelByCharacteristic(characteristic, false) //查看看 isEnable是什麼? 可以取消訂閱嗎?
        RxChannel = characteristic.uuid.toString()
        service.descriptorChannelByCharacteristic(characteristic, true)
    }

    /**
     * Sends a message to a known service by providing the service UUID and characteristic UUID.
     */
    fun send(serviceUUID: UUID, characteristicUUID: UUID, msg: Any, type: DataType) {
        Log.d("YsBT: $TAG", "Set TxChannel: $characteristicUUID")
        TxChannel = characteristicUUID.toString()
        service.send(serviceUUID, characteristicUUID, service.toByteArray(msg, type) ?: return)
    }

    /**
     * Sends a message to a characteristic by directly using the characteristic value obtained
     * through iterating device services when the device information is unknown.
     */
    fun sendByCharacteristic(characteristic: BluetoothGattCharacteristic, msg: Any, type: DataType) {
        Log.d("YsBT: $TAG", "Set TxChannel: ${characteristic.uuid}")
        TxChannel = characteristic.uuid.toString()
        service.sendByCharacteristic(characteristic, service.toByteArray(msg, type) ?: return)
    }

    /**
     * Checks the status of Bluetooth initialization.
     * Checks if BluetoothManager and BluetoothAdapter are initialized.
     */
    fun checkBluetoothStatus(): Boolean {
        if (bluetoothManager == null) {
            Log.d("YsBT: $TAG", "BluetoothManager not initialize")
            return false
        }
        if (bluetoothAdapter == null) {
            Log.d("YsBT: $TAG", "BluetoothAdapter not initialize")
            return false
        }
        return true
    }

    /**
     * Opens Bluetooth on the device.
     * Requests permission if not granted and starts an intent to enable Bluetooth.
     * The duration of discoverability is set to 300 seconds.
     */
    fun openBT() {
        if (!checkAndRequestPermission()) return
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        context.startActivity(intent)
    }

    /**
     * Checks and requests necessary permissions for Bluetooth functionality.
     * Checks if the ACCESS_COARSE_LOCATION permission is granted.
     * If not granted, it requests the permission and returns false.
     */
    fun checkAndRequestPermission(): Boolean {
        return if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            nCallBack.onRequestPermission(arrayListOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            Log.d("YsBT: $TAG", "Need Permission Manifest.permission.ACCESS_COARSE_LOCATION")
            false
        } else true
    }

    fun setIndicate(serviceUUID: UUID, characteristicUUID: UUID) {
        service.setIndicate(serviceUUID, characteristicUUID)
    }

    init {
        initialize()
        registerBTReceiver()
    }
}