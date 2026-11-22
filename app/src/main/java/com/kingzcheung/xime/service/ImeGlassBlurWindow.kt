package com.kingzcheung.xime.service

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.kingzcheung.xime.util.FileLogger

/**
 * 键盘背后、和键盘等大的一块系统模糊面（安卓 12+）。
 *
 * 系统模糊按窗口算，直接给整屏透明的输入法窗口设半径会连键盘以外的画面一起糊掉。
 * 这里用一个 TYPE_APPLICATION_MEDIA 的子窗口：它只覆盖键盘矩形，且被系统合成在父窗口之下，
 * 所以模糊只出现在键盘背后，键帽与文字仍由输入法自己画。
 * 不截图、不冻结位图、不用隐藏 API。
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class ImeGlassBlurWindow(private val context: Context, private val role: String = "keyboard") {

    private var dialog: Dialog? = null
    private var background: GradientDrawable? = null
    private var region: Rect? = null
    private var boundToken: IBinder? = null
    private var blurRadiusPx = 0
    private var cornerRadiusPx = 0f
    private var enabled = false
    private var visible = false
    private var opacity = 1f
    private var appliedRegion: Rect? = null
    private var appliedBlurRadius = -1
    private var appliedCornerRadius = -1f
    private var appliedOpacity = -1f

    /** 键盘矩形（输入法窗口坐标）。换矩形只挪子窗口，不重建。 */
    fun setRegion(bounds: Rect?) {
        val next = bounds?.takeUnless { it.isEmpty }?.let(::Rect)
        if (region == next) return
        region = next
        refresh()
    }

    /** 开关、模糊半径（px）、键盘圆角（px）。 */
    fun configure(enabled: Boolean, blurRadiusPx: Int, cornerRadiusPx: Float = 0f) {
        val radius = blurRadiusPx.coerceAtLeast(0)
        val corner = cornerRadiusPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        if (this.enabled == enabled && this.blurRadiusPx == radius && this.cornerRadiusPx == corner) return
        this.enabled = enabled
        this.blurRadiusPx = radius
        this.cornerRadiusPx = corner
        refresh()
    }

    /** 键盘整体透明度：模糊跟着按键淡出，而不是一直满强度糊住宿主。 */
    fun setOpacity(alpha: Float) {
        val next = alpha.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        if (opacity == next) return
        opacity = next
        refresh()
    }

    /** 键盘是否在屏上：收起时撤掉模糊窗口（与参考实现一致）。 */
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        refresh()
    }

    /** 交给输入法窗口的父视图与 token；窗口重建（主题切换等）后重新绑定。 */
    fun update(parent: View?, token: IBinder?) {
        if (!enabled || !visible || region == null || parent == null || !parent.isAttachedToWindow || token == null) {
            removeWindow()
            return
        }
        try {
            val current = dialog ?: createWindow(parent, token).also {
                dialog = it
                boundToken = token
            }
            updateWindow(current.window ?: return)
            if (!current.isShowing) current.show()
        } catch (t: RuntimeException) {
            FileLogger.w(TAG, "键盘模糊窗口不可用，退回透明背板", t)
            removeWindow()
        }
    }

    fun dispose() {
        removeWindow()
    }

    private fun refresh() {
        if (!enabled || !visible) removeWindow() else dialog?.window?.let { updateWindow(it) }
    }

    private fun createWindow(parent: View, token: IBinder): Dialog {
        val windowContext = parent.display?.let(context::createDisplayContext) ?: context
        val child = Dialog(windowContext, android.R.style.Theme_InputMethod)
        child.setCancelable(false)
        child.setCanceledOnTouchOutside(false)
        val window = checkNotNull(child.window)
        window.requestFeature(Window.FEATURE_NO_TITLE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
        }
        window.setBackgroundDrawable(background)
        window.setDecorFitsSystemWindows(false)
        window.setDimAmount(0f)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA
            this.token = token
            gravity = Gravity.TOP or Gravity.LEFT
            flags = (flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) and
                (WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN).inv()
            format = PixelFormat.TRANSLUCENT
            windowAnimations = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            setTitle("Xime keyboard blur: $role")
        }
        window.decorView.setPadding(0, 0, 0, 0)
        window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        window.setContentView(View(windowContext))
        window.setWindowAnimations(0)
        return child
    }

    private fun updateWindow(window: Window) {
        val bounds = region ?: return
        if (appliedRegion != bounds) {
            window.attributes = window.attributes.apply {
                x = bounds.left
                y = bounds.top
                width = bounds.width()
                height = bounds.height()
            }
            appliedRegion = Rect(bounds)
        }
        val corner = cornerRadiusPx.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2f)
        if (appliedCornerRadius != corner) {
            background?.cornerRadius = corner
            window.decorView.invalidateOutline()
            appliedCornerRadius = corner
        }
        if (appliedOpacity != opacity) {
            window.attributes = window.attributes.apply { alpha = opacity }
            appliedOpacity = opacity
        }
        if (appliedBlurRadius != blurRadiusPx) {
            window.setBackgroundBlurRadius(blurRadiusPx)
            appliedBlurRadius = blurRadiusPx
        }
    }

    private fun removeWindow() {
        val previous = dialog
        dialog = null
        background = null
        boundToken = null
        appliedRegion = null
        appliedBlurRadius = -1
        appliedCornerRadius = -1f
        appliedOpacity = -1f
        if (previous != null) {
            runCatching { previous.window?.setBackgroundBlurRadius(0) }
            runCatching { previous.dismiss() }
        }
    }

    private companion object {
        const val TAG = "ImeGlassBlurWindow"
    }
}
