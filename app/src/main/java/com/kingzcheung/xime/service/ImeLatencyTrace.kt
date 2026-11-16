package com.kingzcheung.xime.service

import android.os.SystemClock
import android.util.Log
import com.kingzcheung.xime.BuildConfig

/**
 * 键盘拉起耗时埋点。
 *
 * 记四段：onStartInput → onCreateInputView → 首帧组合 → 键盘可见。
 * 只在 debug 日志里输出，用来判断"慢"到底慢在引擎初始化、视图创建还是首帧布局。
 */
object ImeLatencyTrace {
    private fun debug(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    private const val TAG = "ImeLatency"

    @Volatile private var startInputAt = 0L
    @Volatile private var createViewAt = 0L
    @Volatile private var firstFrameAt = 0L
    @Volatile private var lastShowAt = 0L

    fun onStartInput() {
        val now = SystemClock.elapsedRealtime()
        // 只统计"从无到有"的一次拉起：键盘已显示时的 onStartInput 不算
        if (startInputAt == 0L || now - startInputAt > 2000) {
            startInputAt = now
            createViewAt = 0
            firstFrameAt = 0
        }
        debug(TAG, "onStartInput")
    }

    fun onStartInputDone() {
        if (startInputAt == 0L) return
        debug(TAG, "onStartInput done +" + (SystemClock.elapsedRealtime() - startInputAt) + "ms")
    }

    fun onCreateInputView() {
        createViewAt = SystemClock.elapsedRealtime()
        debug(TAG, "onCreateInputView +" + delta(startInputAt, createViewAt) + "ms")
    }

    fun onCreateInputViewDone() {
        if (createViewAt == 0L) return
        debug(TAG, "onCreateInputView done +" + (SystemClock.elapsedRealtime() - createViewAt) + "ms")
    }

    fun onFirstComposition() {
        val now = SystemClock.elapsedRealtime()
        if (firstFrameAt == 0L) firstFrameAt = now
        debug(TAG, "firstComposition +" + delta(startInputAt, now) + "ms (fromCreateView " +
                delta(createViewAt, now) + "ms)"
        )
    }

    fun onShown() {
        val now = SystemClock.elapsedRealtime()
        lastShowAt = now
        debug(TAG, "shown +" + delta(startInputAt, now) + "ms")
    }

    private fun delta(from: Long, to: Long): Long = if (from == 0L) -1 else to - from
}