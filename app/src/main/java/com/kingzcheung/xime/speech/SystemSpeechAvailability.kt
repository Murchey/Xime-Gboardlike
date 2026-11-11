package com.kingzcheung.xime.speech

import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * 系统语音引擎可用性探测（不同机型差异很大，需给用户明确原因而不是灰掉不解释）。
 *
 * 三重检查：
 * 1. 包可见性：Android 11+ 默认查不到第三方语音服务，需 manifest 声明 `<queries>`；
 *    未声明时 [check] 返回 [Reason.SERVICE_NOT_FOUND]，配置正确后才会走到后面两步。
 * 2. [SpeechRecognizer.isRecognitionAvailable]：系统是否报告"有可用识别服务"。
 * 3. 实际创建一次 [SpeechRecognizer]：部分机型前两步通过但创建失败（服务崩溃/权限）。
 */
object SystemSpeechAvailability {

    enum class Reason {
        /** 可用。 */
        OK,
        /** 系统没有任何 RecognitionService（含未声明 `<queries>` 导致查不到）。 */
        SERVICE_NOT_FOUND,
        /** 有服务但 [SpeechRecognizer.isRecognitionAvailable] 为 false。 */
        NOT_RECOGNIZED,
        /** 创建 SpeechRecognizer 失败。 */
        CREATE_FAILED,
    }

    data class Result(
        val available: Boolean,
        val reason: Reason,
        /** 提供识别服务的包名（探测不到时为空）。 */
        val servicePackage: String? = null,
    )

    /**
     * 探测系统语音引擎。
     *
     * 分两层：
     * - **轻量探测**（[quickCheck]）：查服务包 + [SpeechRecognizer.isRecognitionAvailable]，
     *   无副作用、可在任意线程调用，供后台/Compose 组合期使用；
     * - **完整探测**（本方法）：额外真实创建一次 [SpeechRecognizer]。
     *   它有**主线程约束**，非主线程创建会抛异常而被误判为"不可用"，
     *   因此非主线程会自动退化为轻量探测结果。
     */
    fun check(context: Context): Result {
        val quick = quickCheck(context)
        if (!quick.available) return quick
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // 非主线程：不做创建校验（会抛异常导致误报不可用）
            return quick
        }
        var created: SpeechRecognizer? = null
        val ok = try {
            created = SpeechRecognizer.createSpeechRecognizer(context)
            created != null
        } catch (e: Throwable) {
            false
        } finally {
            runCatching { created?.destroy() }
        }
        return quick.copy(
            available = ok,
            reason = if (ok) Reason.OK else Reason.CREATE_FAILED,
        )
    }

    /** 轻量探测：不创建实例，任意线程安全。 */
    fun quickCheck(context: Context): Result {
        val pkg = findRecognitionServicePackage(context)
        val recognized = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Throwable) {
            false
        }
        return if (recognized) {
            Result(available = true, reason = Reason.OK, servicePackage = pkg)
        } else {
            Result(
                available = false,
                reason = if (pkg == null) Reason.SERVICE_NOT_FOUND else Reason.NOT_RECOGNIZED,
                servicePackage = pkg,
            )
        }
    }

    /** 查提供识别服务的包名；查不到返回 null（多为 manifest 缺少 `<queries>` 声明）。 */
    private fun findRecognitionServicePackage(context: Context): String? = try {
        val intent = android.content.Intent(RecognitionService.SERVICE_INTERFACE)
        context.packageManager
            .queryIntentServices(intent, PackageManager.MATCH_ALL)
            .firstOrNull()
            ?.serviceInfo
            ?.packageName
    } catch (e: Throwable) {
        null
    }

    /** 系统识别服务显示名（给设置页副标题用）；探测失败返回 null。 */
    fun serviceLabel(context: Context): String? = try {
        val pkg = findRecognitionServicePackage(context) ?: return null
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: Throwable) {
        null
    }
}
