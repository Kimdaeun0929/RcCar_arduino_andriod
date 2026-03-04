package com.example.rccar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private lateinit var tvLog: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvSpeed: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        tvMode = findViewById(R.id.tvMode)
        tvSpeed = findViewById(R.id.tvSpeed)

        // 1. 앱 실행 시 권한부터 확인
        checkBluetoothPermissions()

        // 2. 버튼 리스너 설정
        findViewById<Button>(R.id.btn_line).setOnClickListener { sendData("L"); tvMode.text = "LINE" }
        findViewById<Button>(R.id.btn_idle).setOnClickListener { sendData("I"); tvMode.text = "IDLE" }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { sendData("S"); tvMode.text = "STOP" }
        findViewById<Button>(R.id.btn_led).setOnClickListener { sendData("H") }
        findViewById<Button>(R.id.btn_buzzer).setOnClickListener { sendData("B") }

        // 3. 속도 조절바 설정
        findViewById<SeekBar>(R.id.seekSpeed).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                tvSpeed.text = progress.toString()
                findViewById<TextView>(R.id.tvSpeedVal).text = "Value: $progress"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = seekBar?.progress ?: 120
                sendData("V$speed\n")
            }
        })
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val denied = permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), 1000)
        } else {
            connectToHC06()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            connectToHC06()
        } else {
            addLog("권한이 거부되었습니다.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToHC06() {
        val btManager = getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            addLog("블루투스를 켜주세요.")
            return
        }

        val devices = adapter.bondedDevices
        val targetDevice = devices.find { it.name.contains("MY_CAR", ignoreCase = true) }

        if (targetDevice != null) {
            addLog("${targetDevice.name} 연결 시도 중...")
            Thread {
                try {
                    bluetoothSocket = targetDevice.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()
                    outputStream = bluetoothSocket?.outputStream
                    runOnUiThread { addLog("연결 성공!") }
                } catch (e: Exception) {
                    runOnUiThread { addLog("연결 실패: ${e.message}") }
                }
            }.start()
        } else {
            addLog("페어링된 _MY_CAR을 찾을 수 없습니다.")
        }
    }

    private fun sendData(data: String) {
        if (outputStream == null) return
        Thread {
            try {
                outputStream?.write(data.toByteArray())
            } catch (e: Exception) {
                runOnUiThread { addLog("데이터 전송 실패") }
            }
        }.start()
    }

    private fun addLog(msg: String) {
        runOnUiThread { tvLog.append("\n> $msg") }
    }
}