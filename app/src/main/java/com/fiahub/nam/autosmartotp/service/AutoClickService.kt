package com.fiahub.nam.autosmartotp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.*
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import com.fiahub.nam.autosmartotp.MainActivity
import com.fiahub.nam.autosmartotp.R
import com.fiahub.nam.autosmartotp.logd
import java.lang.Exception


var autoClickService: AutoClickService? = null

class AutoClickService : AccessibilityService() {

    override fun onInterrupt() {
        // NO-OP
    }

    private var onGetOtpCompleted: ((String) -> Unit)? = null

    private var isStarted = false
    private var transferCode = ""
    private var unlockOtpCode = ""

    private var isLoadedLoginScreen = false
    private var isLoadedInputTransCodeScreen = false
    private var isLoadedUnlockOtpScreen = false
    private var isLoadedGeneratedOtpScreen = false

    private val nodeInfos = mutableListOf<AccessibilityNodeInfo>()

    private val listKeyBoardLocation by lazy {

        val displayMetrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(
            displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val buttonWidth = width / 4
        val halfButtonWidth = buttonWidth / 2

        val buttonHeight = resources.getDimension(R.dimen.button_number_height)
        val halfButtonHeight = buttonHeight / 2

        val list = mutableListOf<Triple<Int, Float, Float>>()


        for (i in 1..3) {
            list.add(Triple(i,
                (height - buttonHeight * 3) - halfButtonHeight,
                (width - (5 - i % 4) * buttonWidth).toFloat() + halfButtonWidth))
        }

        for (i in 4..6) {
            list.add(Triple(i,
                (height - buttonHeight * 2) - halfButtonHeight,
                (width - (5 - (i - 3) % 4) * buttonWidth).toFloat() + halfButtonWidth))
        }

        for (i in 7..9) {
            list.add(Triple(i,
                (height - buttonHeight * 1) - halfButtonHeight,
                (width - (5 - (i - 6) % 4) * buttonWidth).toFloat() + halfButtonWidth))
        }

        list.add(Triple(0,
            height.toFloat() - halfButtonHeight,
            (width - 3 * buttonWidth).toFloat() + halfButtonWidth))

        list
    }

    fun isTcbOpening(): Boolean {
        if (nodeInfos.size == 0) {
            getNodeInfo(rootInActiveWindow)
        }

        return nodeInfos.firstOrNull()?.packageName == "com.fastacash.tcb"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        Log.d("nam", event.toString())

        nodeInfos.clear()

        getNodeInfo(rootInActiveWindow)

        handleScreenChanged()
    }

    private fun onFinishGetOtp(otp: String) {
        stopGetOtp()
        onGetOtpCompleted?.invoke(otp)
    }

    private fun dispatchPinUnlockOtp() {

        listKeyBoardLocation.find { it.first == 5 }?.let {

            val path = Path()
            path.moveTo(it.third, it.second)

            val builder = GestureDescription.Builder()
            val gestureDescription = builder
                .addStroke(GestureDescription.StrokeDescription(path, 1, 50))
                .build()

            dispatchGesture(gestureDescription, null, null)
        }

        /**
         * wait for editext showed and then set unclock PIN
         */
        Handler().postDelayed({
            nodeInfos.find { it.className == EditText::class.java.name }?.let {
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    unlockOtpCode)

                it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }

            /**
             * wait for button enabled after input PIN then perform click
             */
            Handler().postDelayed({
                nodeInfos.find { it.text == "Lấy mã OTP" && it.className == Button::class.java.name }
                    ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            }, 200)

        }, 200)


        /* unlockOtpCode.forEachIndexed { index, number ->

             listKeyBoardLocation.find { number.digitToInt() == it.first }?.let {

                 Handler().postDelayed({
                     val path = Path()
                     path.moveTo(it.third, it.second)

                     val builder = GestureDescription.Builder()
                     val gestureDescription = builder
                         .addStroke(GestureDescription.StrokeDescription(path, 1, 50))
                         .build()

                     dispatchGesture(gestureDescription, null, null)
                 }, (index + 1) * 200L)
             }
         }*/
    }

    private fun isLoginScreen(): Boolean {

        //--login screen
        return nodeInfos.find { it.text == "ĐĂNG NHẬP" } != null
    }

    private fun isInputTransIDScreen(): Boolean {

        return nodeInfos.find { it.text == "Nhập mã giao dịch" || it.hintText == "Nhập mã giao dịch" } != null
    }

    private fun isUnclockOtpScreen(): Boolean {

        return nodeInfos.find { it.text == "Nhập mã mở khóa Smart OTP" } != null
    }

    private fun isGeneratedOtpScreen(): Boolean {
        return nodeInfos.find { it.text == "Sao chép mã OTP" } != null
    }


    private fun getNodeInfo(node: AccessibilityNodeInfo?) {

        if (node == null) {
            return
        }

        try {
            if (!node.text.isNullOrEmpty() || !node.hintText.isNullOrEmpty()) {
                nodeInfos.add(node)
            }

            if (node.childCount > 0) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let {
                        getNodeInfo(it)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startGetOtp(transCode: String, _unclockOtpCode: String, onCompleted: (String) -> Unit) {
        isStarted = true
        transferCode = transCode
        this.unlockOtpCode = _unclockOtpCode
        onGetOtpCompleted = onCompleted

        if (nodeInfos.isEmpty()) {
            getNodeInfo(rootInActiveWindow)
        }
        handleScreenChanged()
    }

    fun stopGetOtp() {
        isStarted = false
        isLoadedLoginScreen = false
        isLoadedInputTransCodeScreen = false
        isLoadedUnlockOtpScreen = false
        isLoadedGeneratedOtpScreen = false
    }

    private fun handleScreenChanged() {

        if (!isStarted || !isTcbOpening()) {
            return
        }

        when {
            isLoginScreen() && !isLoadedLoginScreen -> {

                isLoadedLoginScreen = true

                nodeInfos.find { it.text == "Lấy mã OTP" }
                    ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            }

            isInputTransIDScreen() && !isLoadedInputTransCodeScreen -> {

                isLoadedInputTransCodeScreen = true

                nodeInfos.find { it.hintText == "Nhập mã giao dịch" }?.let {

                    val arguments = Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        transferCode)
                    it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                    /**
                     * delay time for button enabled
                     */
                    Handler().postDelayed({
                        nodeInfos.find { it.text == "Xác nhận" }
                            ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                    }, 100)
                }
            }

            isUnclockOtpScreen() && !isLoadedUnlockOtpScreen -> {

                isLoadedUnlockOtpScreen = true

                nodeInfos.find { it.text == "Lấy mã OTP" && it.className == Button::class.java.name }
                    ?.let {

                        val rect = Rect()
                        it.getBoundsInScreen(rect)

                        val path = Path()
                        path.moveTo(rect.exactCenterX(), rect.top.toFloat() - 30)

                        val builder = GestureDescription.Builder()
                        val gestureDescription = builder
                            .addStroke(GestureDescription.StrokeDescription(path, 1, 10))
                            .build()
                        dispatchGesture(gestureDescription, null, null)
                    }

                /**
                 * wait for keyboard showed and dispatch key press PIN
                 */
                Handler().postDelayed({ dispatchPinUnlockOtp() }, 1500)
            }

            isGeneratedOtpScreen() && !isLoadedGeneratedOtpScreen -> {
                isLoadedGeneratedOtpScreen = true

                nodeInfos.find { it.text == "Mã OTP của quý khách" }?.let {

                    nodeInfos.getOrNull(nodeInfos.indexOf(it) + 1)?.let {
                        Handler().postDelayed({ onFinishGetOtp(it.text.toString()) }, 300)
                    }
                }

                nodeInfos.find { it.text == "Giao dịch khác" }?.let {
                    it.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        "onServiceConnected".logd()
        autoClickService = this
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        "AutoClickService onUnbind".logd()
        autoClickService = null
        return super.onUnbind(intent)
    }


    override fun onDestroy() {
        "AutoClickService onDestroy".logd()
        autoClickService = null
        super.onDestroy()
    }


}