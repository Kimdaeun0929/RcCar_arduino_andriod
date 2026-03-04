package com.example.rccar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.OutputStream
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private lateinit var tvLog: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvSpeed: TextView

    private lateinit var joystickBase: View
    private lateinit var joystickThumb: View

    private lateinit var scrollLog: ScrollView
    private var maxRadius = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        tvMode = findViewById(R.id.tvMode)
        tvSpeed = findViewById(R.id.tvSpeed)

        joystickBase = findViewById(R.id.joystickBase)
        joystickThumb = findViewById(R.id.joystickThumb)

        scrollLog = findViewById(R.id.scrollLog)
        tvLog = findViewById(R.id.tvLog)
        checkBluetoothPermissions()

        // 버튼
        findViewById<Button>(R.id.btn_line).setOnClickListener {
            sendData("L\n")
            tvMode.text = "LINE"
        }

        findViewById<Button>(R.id.btn_idle).setOnClickListener {
            sendData("M\n")  // 수동 모드로 사용
            tvMode.text = "MANUAL"
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            sendData("S\n")
            tvMode.text = "STOP"
        }

        findViewById<Button>(R.id.btn_led).setOnClickListener {
            sendData("LED\n")
        }

        findViewById<Button>(R.id.btn_buzzer).setOnClickListener {
            sendData("BUZ\n")
        }

        // 속도바
        findViewById<SeekBar>(R.id.seekSpeed)
            .setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    tvSpeed.text = progress.toString()
                    findViewById<TextView>(R.id.tvSpeedVal).text =
                        "Value: $progress"
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val speed = seekBar?.progress ?: 120
                    sendData("V$speed\n")
                }
            })

        setupJoystick()
    }

    // ================= JOYSTICK =================

    private fun setupJoystick() {

        joystickBase.post {
            maxRadius = joystickBase.width / 2f
        }

        joystickBase.setOnTouchListener { _, event ->

            val centerX = joystickBase.width / 2f
            val centerY = joystickBase.height / 2f

            val dx = event.x - centerX
            val dy = event.y - centerY

            val distance = sqrt(dx * dx + dy * dy)

            when (event.action) {

                MotionEvent.ACTION_MOVE -> {

                    val ratio =
                        if (distance > maxRadius)
                            maxRadius / distance
                        else 1f

                    val limitedX = dx * ratio
                    val limitedY = dy * ratio

                    joystickThumb.x =
                        joystickBase.x + centerX +
                                limitedX - joystickThumb.width / 2

                    joystickThumb.y =
                        joystickBase.y + centerY +
                                limitedY - joystickThumb.height / 2

                    val angle =
                        Math.toDegrees(
                            atan2(
                                limitedY.toDouble(),
                                limitedX.toDouble()
                            )
                        )

                    val strength =
                        min(100f, (distance / maxRadius) * 100)

                    controlCar(angle, strength)
                }

                MotionEvent.ACTION_UP -> {

                    joystickThumb.animate()
                        .x(joystickBase.x + centerX -
                                joystickThumb.width / 2)
                        .y(joystickBase.y + centerY -
                                joystickThumb.height / 2)
                        .setDuration(100)
                        .start()

                    sendData("S\n")
                }
            }
            true
        }
    }

    // 방향 계산
    private fun controlCar(angle: Double, strength: Float) {

        if (strength < 10) {
            sendData("S\n")
            return
        }

        val pwm = (strength * 2.55).toInt()

        when {
            angle >= -45 && angle <= 45 ->
                sendData("F$pwm\n")

            angle > 45 && angle < 135 ->
                sendData("R$pwm\n")

            angle < -45 && angle > -135 ->
                sendData("L$pwm\n")

            else ->
                sendData("B$pwm\n")
        }
    }

    // ================= BLUETOOTH =================

    private fun checkBluetoothPermissions() {

        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            else
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

        val denied =
            permissions.filter {
                ActivityCompat.checkSelfPermission(
                    this, it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                denied.toTypedArray(),
                1000
            )
        } else connectToHC06()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1000 &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            connectToHC06()
        } else {
            addLog("권한이 거부되었습니다.")
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToHC06() {

        val btManager =
            getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter

        val targetDevice =
            adapter?.bondedDevices?.find {
                it.name.contains("MY_CAR", true)
            }

        if (targetDevice != null) {
            Thread {
                try {
                    bluetoothSocket =
                        targetDevice.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()
                    outputStream =
                        bluetoothSocket?.outputStream
                    runOnUiThread {
                        addLog("연결 성공!")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        addLog("연결 실패")
                    }
                }
            }.start()
        }
    }

    private fun sendData(data: String) {

        if (outputStream == null) {
            addLog("블루투스 연결 안됨")
            return
        }

        Thread {
            try {
                outputStream?.write(data.toByteArray())
                runOnUiThread {
                    addLog("SEND → ${data.trim()}")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("전송 실패: ${e.message}")
                }
            }
        }.start()
    }


    private fun addLog(msg: String) {
        tvLog.append("\n> $msg")

        scrollLog.post {
            scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }
}