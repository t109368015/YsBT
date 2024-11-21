package com.ys.bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*

class BTServiceControl(private val context: Context, private val btHelper: BTHelper) {
    private val LIST_NAME = "NAME"
    private val LIST_UUID = "UUID"
    private var TAG = BTServiceControl::class.java.simpleName
    private var gatt: BluetoothGatt? = null
    private var serviceList = ArrayList<BluetoothGattService>()
    var isConnect = false

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                if (!btHelper.checkAndRequestPermission()) return
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.d("YsBT: $TAG", "STATE_CONNECTED")
                        isConnect = true
                        gatt.discoverServices()
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.d("YsBT: $TAG", "STATE_DISCONNECTED")
                        isConnect = false
                        btHelper.nCallBack.onConnectionStateChange(false, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("YsBT: $TAG", "BluetoothGattCallback Error: $e")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("YsBT: $TAG", "GATT_SUCCESS")
                    serviceList.clear()
                    serviceList.addAll(gatt.services)
                    btHelper.nCallBack.onConnectionStateChange(true, gatt.services)
                } else {
                    Log.e("YsBT: $TAG", "GATT_FAILED: $status")
                }
            } catch (e: Exception) {
                Log.e("YsBT: $TAG", "onServicesDiscovered Error: $e")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("YsBT: $TAG", "Tx: ${characteristic.uuid} -> ${Binary(characteristic.value ?: ByteArray(0)).toHEX()}")
                    btHelper.nCallBack.tx("${characteristic.uuid ?: "UnKnow"}", characteristic.value)
                } else {
                    Log.e("YsBT: $TAG", "Tx: send error")
                }
            } catch (e: Exception) {
                Log.e("YsBT: $TAG", "onCharacteristicWrite Error: $e")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            try {
                val uuid = characteristic?.uuid ?: "UnKnow"
                if (uuid.toString() == btHelper.RxChannel) {
                    Log.d("YsBT: ", "Rx: $uuid -> ${Binary(characteristic?.value ?: ByteArray(0)).toHEX()}")
                    btHelper.nCallBack.rx("${characteristic?.uuid ?: "UnKnow"}", characteristic?.value)
                    btHelper.RxData = Binary(characteristic?.value ?: return).toHEX()
                }
            } catch (e: Exception) {
                Log.e("YsBT: $TAG", "onCharacteristicChanged Error: $e")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            try {
                super.onDescriptorWrite(gatt, descriptor, status)
                Log.d("YsBT: $TAG", "onDescriptorWrite: ${btHelper.RxChannel}, status: $status")
            } catch (e: Exception) {
                Log.e("YsBT: $TAG", "onDescriptorWrite Error: $e")
            }
        }
    }

    fun connect(address: String) {
        if (!btHelper.checkAndRequestPermission()) return
        if (!btHelper.checkBluetoothStatus()) return

        val device = btHelper.bluetoothAdapter!!.getRemoteDevice(address) ?: run {
            Log.d("YsBT: $TAG", "Device not found. Unable to connect.")
            return
        }

        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun descriptorChannel(serviceUUID: UUID, characteristicUUID: UUID, isEnable: Boolean) {
        try {
            if (!btHelper.checkAndRequestPermission() || !btHelper.checkBluetoothStatus()) return

            val nService: BluetoothGattService = gatt?.getService(serviceUUID) ?: return
            val nCharacteristic: BluetoothGattCharacteristic? = nService.getCharacteristic(characteristicUUID)

            gatt?.setCharacteristicNotification(nCharacteristic, isEnable)
            val descriptor = nCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)

            if (isEnable)
                Log.d("YsBT: $TAG", "DescriptorChannel -> serviceUUID: $serviceUUID, characteristicUUID: $characteristicUUID")
            else
                Log.d("YsBT: $TAG", "CancelChannel -> serviceUUID: $serviceUUID, characteristicUUID: $characteristicUUID")
        } catch (e: Exception) {
            Log.e("YsBT: $TAG", "DescriptorChannel error: $e")
        }
    }

    fun descriptorChannelByCharacteristic(characteristic: BluetoothGattCharacteristic, isEnable: Boolean) {
        try {
            if (!btHelper.checkAndRequestPermission() || !btHelper.checkBluetoothStatus()) return
            gatt?.setCharacteristicNotification(characteristic, isEnable)
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)

            if (isEnable)
                Log.d("YsBT: $TAG", "DescriptorChannel -> characteristicUUID: ${characteristic.uuid}")
            else
                Log.d("YsBT: $TAG", "CancelChannel -> characteristicUUID: ${characteristic.uuid}")
        } catch (e: Exception) {
            Log.e("YsBT: $TAG", "DescriptorChannel error: $e")
        }
    }

    @SuppressLint("MissingPermission")
    fun setIndicate(serviceUUID: UUID, characteristicUUID: UUID) {
        val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)

        gatt?.setCharacteristicNotification(characteristic, true)

        val descriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic?.getDescriptor(descriptorUUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        gatt?.writeDescriptor(descriptor)
    }

    fun send(serviceUUID: UUID, characteristicUUID: UUID, msg: ByteArray) {
        try {
            if (!btHelper.checkAndRequestPermission() || !btHelper.checkBluetoothStatus()) return
            val nService: BluetoothGattService? = gatt?.getService(serviceUUID)
            val nCharacteristic: BluetoothGattCharacteristic? = nService?.getCharacteristic(characteristicUUID)
            nCharacteristic?.value = msg
            gatt?.writeCharacteristic(nCharacteristic)
            Log.d("YsBT: $TAG", "Send -> serviceUUID: ${serviceUUID}, characteristicUUID: ${characteristicUUID}, msg: $msg")
        } catch (e: Exception) {
            Log.e("YsBT: $TAG", "Send error: $e")
        }
    }

    fun sendByCharacteristic(characteristic: BluetoothGattCharacteristic, msg: ByteArray) {
        try {
            if (!btHelper.checkAndRequestPermission() || !btHelper.checkBluetoothStatus()) return
            characteristic.value = msg
            gatt?.writeCharacteristic(characteristic)
            Log.d("YsBT: $TAG", "Send -> characteristicUUID: ${characteristic.uuid}, msg: $msg")
        } catch (e: Exception) {
            Log.e("YsBT: $TAG", "Send error: $e")
        }
    }

    fun displayGattServices(gattServices: List<BluetoothGattService>?): ArrayList<BluetoothGattCharacteristic>? {
        if (gattServices == null) return null
        var uuid: String? = null
        val unknownServiceString = "unknownServiceString"
        val unknownCharaString = "unknownCharaString"
        val gattServiceData = ArrayList<HashMap<String, String>>()
        val gattCharacteristicData = ArrayList<ArrayList<HashMap<String, String>>>()
        val characteristicsList = ArrayList<BluetoothGattCharacteristic>()
        for (gattService in gattServices) {
            val currentServiceData = HashMap<String, String>()
            uuid = gattService.uuid.toString()
            Log.d("YsBT: $TAG", "uuid-Server: ${uuid}")
            currentServiceData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownServiceString)
            currentServiceData[LIST_UUID] = uuid
            gattServiceData.add(currentServiceData)

            val gattCharacteristicGroupData = ArrayList<HashMap<String, String>>()
            val gattCharacteristics = gattService.characteristics

            for (gattCharacteristic in gattCharacteristics) {
                characteristicsList.add(gattCharacteristic)
                val currentCharaData = HashMap<String, String>()
                uuid = gattCharacteristic.uuid.toString()
                Log.d("YsBT: $TAG", "uuid-Channel: uuid")
                currentCharaData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownCharaString)
                currentCharaData[LIST_UUID] = uuid
                gattCharacteristicGroupData.add(currentCharaData)
            }
            gattCharacteristicData.add(gattCharacteristicGroupData)
        }

        return characteristicsList
    }

    fun getServiceList() = serviceList

    fun close() {
        if (!btHelper.checkAndRequestPermission()) return
        if (gatt == null) return
        gatt?.close()
        gatt = null
    }

    fun toByteArray(msg: Any, type: BTHelper.DataType): ByteArray? {
        try {
            return when (type) {
                BTHelper.DataType.Hex -> {
                    if (msg !is String)  {
                        Log.e("BT: $TAG", "Type error")
                        return null
                    }
                    if (msg.length % 2 != 0) {
                        Log.e("BT: $TAG", "Must have an even length")
                        return null
                    }
                    msg.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
                BTHelper.DataType.ByteArray -> {
                    if (msg !is ByteArray) {
                        Log.e("BT: $TAG", "Type error")
                        return null
                    } else msg
                }
                BTHelper.DataType.String -> {
                    (msg as? String)?.toByteArray(Charsets.UTF_8) ?: run {
                        Log.e("BT: $TAG", "Type error")
                        return null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BT: $TAG", "toByteArray error: $e")
            return null
        }
    }
}