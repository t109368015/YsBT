package com.ys.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.Gson
import com.ys.bt.databinding.ItemDetailInnerBinding
import com.ys.bt.databinding.ItemServiceDetailBinding

class BTDetailAdapter(private var context: Context, private var list: List<BluetoothGattService>): ArrayAdapter<BluetoothGattService>(context, 0, list) {
    private var listener: BTListClickListener? = null
    interface BTListClickListener {
        fun onSend(service: BluetoothGattService, characteristic: BluetoothGattCharacteristic)
        fun onGet(service: BluetoothGattService,characteristic: BluetoothGattCharacteristic)
    }

    fun setListener(l: BTListClickListener) { listener = l }

    private class ViewHolder(v: View) {
        val tvStatus: TextView = v.findViewById<TextView>(R.id.tvStatus)
        val tvAddress: TextView = v.findViewById<TextView>(R.id.tvAddress)
        val tvService: TextView = v.findViewById<TextView>(R.id.tvService)
        val llTest: LinearLayout = v.findViewById<LinearLayout>(R.id.llTest)
    }

    override fun getCount(): Int {
        return list.size
    }

    @SuppressLint("InflateParams", "MissingPermission")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = ItemServiceDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
            view = convertView
        }

        val item = getItem(position) ?: return view

        holder.tvAddress.text = "${item.uuid}"
        holder.tvService.text = if (item.type == 0) "SERVICE_TYPE_PRIMARY" else "SERVICE_TYPE_SECONDARY"
        holder.tvStatus.text = "${item.instanceId}"

        Log.d(".ble", "item.uuid: ${item.uuid}")

        holder.llTest.removeAllViews()
        for (i in item.characteristics) {
            val group = ItemDetailInnerBinding.inflate(LayoutInflater.from(parent.context), parent, false)

            group.tvProperties.text = checkProperties(i.properties)
            group.tvAddress.text = "${i.uuid}"
            group.tvService.text = if (i.service.type == 0) "SERVICE_TYPE_PRIMARY" else "SERVICE_TYPE_SECONDARY"
            group.tvStatus.text = "UnKnow Characteristic"

            var str = ""
            for (j in i.descriptors) {
                if (str.isNotEmpty()) str += "\n"
                str += "uuid: ${i.uuid}\nvalue: ${j.characteristic.value}\nproperties: ${checkProperties(j.characteristic.properties)}"
            }

            group.Descriptors.visibility = if (str.isEmpty()) View.GONE else View.VISIBLE
            group.tvDescriptors.visibility = if (str.isEmpty()) View.GONE else View.VISIBLE
            group.tvDescriptors.text = str

            val pro = checkProperties(i.properties)
            group.tvGet.visibility = if (pro.contains("Read")) View.VISIBLE else View.GONE
            group.tvSend.visibility = if (pro.contains("Write")) View.VISIBLE else View.GONE

            group.tvSend.setOnClickListener { listener?.onSend(item, i) }
            group.tvGet.setOnClickListener { listener?.onGet(item, i) }

            holder.llTest.addView(group.root)
        }

        return view
    }

    private fun checkProperties(index: Int): String {
        var str = ""
        if (index and BluetoothGattCharacteristic.PROPERTY_READ != 0) str += "Read"
        if (index and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            str += if (str.isEmpty()) "Write" else ", Write"
        }
        if (index and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            str += if (str.isEmpty()) "Notify" else ", Notify"
        }
        if (index and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            str += if (str.isEmpty()) "Write No Response" else ", Write No Response"
        }
        return str
    }
}