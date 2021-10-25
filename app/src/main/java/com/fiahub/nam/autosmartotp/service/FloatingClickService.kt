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
import com.fiahub.nam.autosmartotp.service.api.ApiService
import com.fiahub.nam.autosmartotp.service.api.PendingTransaction
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class FloatingClickService : Service() {
    private lateinit var manager: WindowManager
    private lateinit var view: RelativeLayout
    private lateinit var params: WindowManager.LayoutParams
    private var xForRecord = 0
    private var yForRecord = 0
    private var startDragDistance: Int = 0

    private var isOn = false
    private var otpPin = ""


    companion object {
        val POOLING_INTERVAL = 3000L
        val RESTART_INTERVAL = 15000L
        val PARAM_UNLOCK_OTP_PIN = "PARAM_UNLOCK_OTP_PIN"

        fun start(context: Context, pin: String) {
            context.startService(Intent(context, FloatingClickService::class.java).apply {
                putExtra(PARAM_UNLOCK_OTP_PIN, pin)
            })

        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        otpPin = intent?.getStringExtra(PARAM_UNLOCK_OTP_PIN) ?: ""
        return super.onStartCommand(intent, flags, startId)
    }

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
            {
                isOn = !isOn

                if (isOn) {
                    startGetOtp()
                } else {
                    stopGetOtp()
                }
                view.findViewById<TextView>(R.id.button).text = if (isOn) "ON" else "OFF"
            },
            { manager.updateViewLayout(view, params) }))

        //getting windows services and adding the floating view to it
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.addView(view, params)
    }


    private val restartHandler = Handler()

    private var restartCallback = Runnable {
        if (isOn) {
            autoClickService?.stopGetOtp()
            startGetOtp()
        }
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
            pendingTransactionJob.run()
        }
    }

    private fun stopGetOtp() {
        autoClickService?.stopGetOtp()
        restartHandler.removeCallbacks(restartCallback)
        pendingTransactionHandler.removeCallbacks(pendingTransactionJob)
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

    private val pendingTransactionHandler = Handler()

    private val pendingTransactionJob = Runnable {

        ApiService.apiService.getPendingTransaction().observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io()).subscribe({

                it.transID.takeIf { !it.isNullOrEmpty() }?.also {
                    onGetTransIDSuccess(it)
                } ?: kotlin.run {
                    onGetTransIDFailed()
                }

            }, {
                it.printStackTrace()
                onGetTransIDFailed()
            })
    }

    private fun onGetTransIDSuccess(transID: String) {
        autoClickService?.startGetOtp(transID, otpPin, ::onGetSmartOtpSuccess)

        restartHandler.removeCallbacks(restartCallback)
        restartHandler.postDelayed(restartCallback, RESTART_INTERVAL)

        pendingTransactionHandler.removeCallbacks(pendingTransactionJob)
    }

    private fun onGetTransIDFailed() {
        pendingTransactionHandler.postDelayed(pendingTransactionJob, POOLING_INTERVAL)
    }

    private fun onGetSmartOtpSuccess(otp: String) {
        Toast.makeText(this, otp, Toast.LENGTH_LONG).show()

        restartHandler.removeCallbacks(restartCallback)

        pendingTransactionJob.run()
    }
}