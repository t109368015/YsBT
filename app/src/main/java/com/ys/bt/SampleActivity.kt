package com.ys.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import com.ys.bt.databinding.ActivitySampleBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.ArrayList
import kotlin.math.abs

class SampleActivity : AppCompatActivity(), BTCallBack {
    private lateinit var binding: ActivitySampleBinding
    private lateinit var adapter: BTSpecifyAdapter
    private lateinit var btHelper: BTHelper
    private var dataList = ArrayList<BTSpecifyAdapter.BTData>()
    private var specifyMac = ""
    private var timeTag = 0L
    private var log = ""
    private var savePerMinute = 60
    private val maxLog = 300
    private var timer: Timer = Timer()
    private var originSize = 0
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {
            originSize = binding.edSearch.text.length
        }

        override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(editable: Editable?) {
            //formatBluetoothMac(binding.edSearch)
        }
    }

    override fun onRequestPermission(list: ArrayList<String>) { checkAndRequestPermission(list[0], 0) }

    override fun onScanDeviceResult(device: BluetoothDevice, scanRecord: Binary, rssi: Int) {
        if (device.address.uppercase().contains(specifyMac.uppercase())) {
            val res = scanRecord.toHEX().replace("0x", "").substring(0, 60)
            var data = ""
            for (i in res.indices step 2) {
                data += "0x${res.substring(i, i + 2)} "
            }

            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            dataList.add(BTSpecifyAdapter.BTData(device.address, date, data))
            if (dataList.size > maxLog) {
                dataList.clear()
            }

            log += "$date\n${device.address} -> \n$data\n\n"
            Log.e("scanBack123", "device: ${device.address}, byteArray: $data")

            val current = System.currentTimeMillis()
            try {
                if (abs(current - timeTag) > savePerMinute * 60 * 1000) { save(current) }
            } catch (e: Exception) {
                timeTag = current
                Log.e("sys:", "local save failed: ${e.message}")
            }
        }
    }

    override fun onStatusChange(status: Int) {
        when (status) {
            BluetoothAdapter.STATE_OFF -> { btStatusChange(false) }
            BluetoothAdapter.STATE_TURNING_OFF -> {}
            BluetoothAdapter.STATE_ON -> { btStatusChange(true) }
            BluetoothAdapter.STATE_TURNING_ON -> {}
        }
    }

    override fun rx(uuid: String, value: ByteArray?) {
        //TODO("Not yet implemented")
    }

    override fun tx(uuid: String, value: ByteArray?) {
        //TODO("Not yet implemented")
    }

    override fun onConnectionStateChange(isConnect: Boolean, serviceList: List<BluetoothGattService>?) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                0 -> checkAndRequestPermission(Manifest.permission.BLUETOOTH_CONNECT, 1)
                1 -> checkAndRequestPermission(Manifest.permission.BLUETOOTH_SCAN, 2)
                2 -> if (!::btHelper.isInitialized) initBTHelper()
            }
        }
    }

    override fun onDestroy() {
        save(System.currentTimeMillis())
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySampleBinding.inflate(layoutInflater)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(binding.root)
        init()
        setListener()
    }

    private fun init() {
        if (checkAndRequestPermission(Manifest.permission.BLUETOOTH_CONNECT, 1) &&
            checkAndRequestPermission(Manifest.permission.BLUETOOTH_SCAN, 2) && checkAndRequestPermission(Manifest.permission.BLUETOOTH_SCAN, 3)) {
            initBTHelper()
        }

        if (!::adapter.isInitialized) {
            adapter = BTSpecifyAdapter(this, dataList)
            binding.listView.adapter = adapter
        }
    }

    private fun initBTHelper() {
        btHelper = BTHelper(this, this)
        btStatusChange(btHelper.isBTOpen)
    }

    private fun setListener() {
        binding.run {
            btnScan.setOnClickListener {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(binding.edSearch.windowToken, 0)
                if (!checkBT()) return@setOnClickListener

                btnScan.setBackgroundColor(resources.getColor(R.color.gray_6E6E6E))
                btStop.setBackgroundColor(resources.getColor(R.color.orange_FF5733))

                dataList.clear()
                adapter.notifyDataSetChanged()
                btHelper.scanDevice()
                specifyMac = binding.edSearch.text.toString()
                timeTag = System.currentTimeMillis()
                timer = Timer()
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            if (::adapter.isInitialized) {
                                val current = System.currentTimeMillis()
                                tvTime.text = timeCount(savePerMinute, timeTag, current)
                                try {
                                    if (abs(current - timeTag) > savePerMinute * 60 * 1000) { save(current) }
                                } catch (e: Exception) {
                                    timeTag = current
                                    Log.e("sys:", "local save failed: ${e.message}")
                                }
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }, 0, if (specifyMac.isEmpty()) 2000 else 500)
            }

            btStop.setOnClickListener {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(binding.edSearch.windowToken, 0)
                if (!checkBT()) return@setOnClickListener
                btHelper.stopScanDevice()
                timer.cancel()
                timer.purge()

                btnScan.setBackgroundColor(resources.getColor(R.color.orange_FF5733))
                btStop.setBackgroundColor(resources.getColor(R.color.gray_6E6E6E))
            }

            btSave.setOnClickListener {
                if (timeTag != 0L) save(System.currentTimeMillis())
            }

            edSearch.addTextChangedListener(textWatcher)
        }
    }

    private fun save(current: Long) {
        if (Build.VERSION.SDK_INT > 28) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Download/BT_Log")
                put(MediaStore.Images.Media.DISPLAY_NAME, "BT_Log_${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date(timeTag))}.txt")
                put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
            }

            val resolver = contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Files.getContentUri("external"), values)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(log.toByteArray())
                }
            }
            Log.e("sys:", "local save succeed, path: ${uri?.path}")
        } else {
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val btLogDir = File(downloadDir, "BT_Log")

                if (!btLogDir.exists()) {
                    btLogDir.mkdirs()
                }

                val file = File(btLogDir, "BT_Log_${SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(Date(timeTag))}.txt")

                FileOutputStream(file).use { outputStream ->
                    outputStream.write(log.toByteArray())
                }

                Log.e("sys:", "local save succeed, path: ${file.path}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        timeTag = current
        log = ""
        runOnUiThread { Toast.makeText(this, "local save succeed", Toast.LENGTH_SHORT).show() }
    }

    private fun timeCount(setMinute: Int, origin: Long, current: Long): String {
        val sec = setMinute * 60 - ((current - origin) / 1000)

        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val remainingSeconds = sec % 60

        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }

    private fun btStatusChange(isOpen: Boolean) {
        binding.clBTNotOpen.visibility = if (isOpen) View.GONE else View.VISIBLE
    }

    private fun checkBT(): Boolean {
        if (!::btHelper.isInitialized) initBTHelper()
        return if (!btHelper.isBTOpen) {
            btHelper.openBT()
            false
        } else true
    }

    fun checkAndRequestPermission(permission: String, TAG: Int): Boolean {
        return if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, Array<String>(1) { permission }, TAG)
            false
        } else true
    }
}