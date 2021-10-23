package com.fiahub.nam.autosmartotp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.fiahub.nam.autosmartotp.R
import com.fiahub.nam.autosmartotp.TouchAndDragListener
import com.fiahub.nam.autosmartotp.dp2px
import com.fiahub.nam.autosmartotp.logd


/**
 * Created on 2018/9/28.
 * By nesto
 */
class FloatingClickService : Service() {
    private lateinit var manager: WindowManager
    private lateinit var view: RelativeLayout
    private lateinit var params: WindowManager.LayoutParams
    private var xForRecord = 0
    private var yForRecord = 0

    private var startDragDistance: Int = 0


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startDragDistance = dp2px(10f)
        view = LayoutInflater.from(this).inflate(R.layout.widget, null) as RelativeLayout

        //setting the layout parameters
        val overlayParam =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        //adding an touchlistener to make drag movement of the floating widget
        view.setOnTouchListener(TouchAndDragListener(params, startDragDistance,
            { viewOnClick() },
            { manager.updateViewLayout(view, params) }))

        //getting windows services and adding the floating view to it
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.addView(view, params)
    }

    private var isOn = false

    private var restartCallback = Runnable {
        if (isOn) {
            autoClickService?.stopGetOtp()
            startGetOtp()
        }
    }

    private val restartHandler = Handler()

    private fun viewOnClick() {

        isOn = !isOn

        if (isOn) {
            startGetOtp()
        } else {
            stopGetOtp()
        }
        view.findViewById<TextView>(R.id.button).text = if (isOn) "ON" else "OFF"
    }

    private fun stopGetOtp() {
        autoClickService?.stopGetOtp()
        restartHandler.removeCallbacks(restartCallback)
    }

    private fun startGetOtp() {

        if (!isOn) {
            return
        }

        if (autoClickService?.isTcbOpening() == false) {
            openAppTcb()

            /**
             * wait for tcb app is opened
             */
            Handler().postDelayed({
                startGetOtp()
            }, 5000)
        } else {

            autoClickService?.startGetOtp("80706224", "1000") {

                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                isOn = false
                view.findViewById<TextView>(R.id.button).text = "OFF"
                restartHandler.removeCallbacks(restartCallback)
            }

            restartHandler.removeCallbacks(restartCallback)
            restartHandler.postDelayed(restartCallback, 15000)
        }
    }

    private fun openAppTcb() {
        val packageName = "com.fastacash.tcb"
        if (isAppInstalled(this, packageName)) {
            startActivity(packageManager.getLaunchIntentForPackage(packageName))
        } else {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAppInstalled(context: Context, packageName: String?): Boolean {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.removeView(view)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        "FloatingClickService onConfigurationChanged".logd()
        val x = params.x
        val y = params.y
        params.x = xForRecord
        params.y = yForRecord
        xForRecord = x
        yForRecord = y
        manager.updateViewLayout(view, params)
    }
}