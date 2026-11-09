package com.kingzcheung.xime.glide

/**
 * 阶段B：统一手势所有权仲裁（纯状态机，不依赖 Compose / Android）。
 *
 * 计划书要求：一次 pointer 序列最多产生一种输入动作；Glide 一旦接管，
 * 长按、单键方向动作、取消补点击都必须取消；不保留左右滑光标。
 * UI 层只需把 DOWN/MOVE/UP/CANCEL 与"起点是否为有效字母键"喂进来，
 * 再按本对象返回的 [Decision] 决定是否执行原有动作。
 */
class GlideGestureArbiter(
    /** 起点是否为可参与滑动的字母/韵母键（由几何快照判定）。 */
    private val eligibleStart: () -> Boolean,
    /** 当前是否允许滑动输入（设置开关 + 中文 26 键方案）。 */
    private val glideEnabled: () -> Boolean,
    /** 超过该位移（键盘局部 px）才考虑从 Pending 升级；小于则视为抖动。 */
    private val slopPx: Float = 12f,
    /** 起点之外经过至少这么多不同键才认定为 Glide（防上滑符号误判）。 */
    private val minDistinctKeys: Int = 2,
) {
    enum class Owner { NONE, PENDING, TAP, LONG_PRESS, GLIDE, CANCELLED }
    enum class Decision {
        /** 继续收集，不执行任何输入动作（不显示长按、不触发方向动作、不补点击）。 */
        COLLECT,
        /** 按普通点按处理（仅 TAP 结束且未升级为 Glide）。 */
        EMIT_TAP,
        /** 长按生效（超过长按时限仍在起点附近）。 */
        EMIT_LONG_PRESS,
        /** 本次滑动结束，交给轨迹解码。 */
        EMIT_GLIDE,
        /** 丢弃，不产生任何动作、不补点击。 */
        DISCARD,
    }

    data class State(
        val owner: Owner = Owner.NONE,
        val pointerId: Long? = null,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val distinctKeys: Int = 0,
        val maxDisplacement: Float = 0f,
        val hasSecondPointer: Boolean = false,
    ) {
        val isGlide: Boolean get() = owner == Owner.GLIDE
        val active: Boolean get() = owner != Owner.NONE && owner != Owner.CANCELLED && owner != Owner.TAP
    }

    @Volatile private var _state = State()
    val state: State get() = _state

    fun onDown(pointerId: Long, x: Float, y: Float, startKeyId: String?): Decision {
        _state = State(
            owner = if (glideEnabled() && eligibleStart() && startKeyId != null) Owner.PENDING else Owner.NONE,
            pointerId = pointerId,
            startX = x, startY = y,
            distinctKeys = if (startKeyId != null) 1 else 0,
        )
        return Decision.COLLECT
    }

    /**
     * MOVE：传入当前 pointer、坐标、命中的键 id（可为 null）、以及是否出现第二根手指。
     * 返回本帧应执行的动作：升级为 Glide 后一律 COLLECT（UI 不执行原键动作）。
     */
    fun onMove(pointerId: Long, x: Float, y: Float, keyId: String?, secondPointer: Boolean): Decision {
        val cur = _state
        if (cur.pointerId == null || cur.owner == Owner.CANCELLED || cur.owner == Owner.TAP) return Decision.COLLECT
        if (secondPointer) {
            _state = cur.copy(owner = Owner.CANCELLED, hasSecondPointer = true)
            return Decision.DISCARD
        }
        if (pointerId != cur.pointerId) return Decision.COLLECT // 忽略其他手指
        val dx = x - cur.startX; val dy = y - cur.startY
        val disp = kotlin.math.hypot(dx, dy)
        val distinct = if (keyId != null) (cur.distinctKeys + 1).coerceAtLeast(cur.distinctKeys) else cur.distinctKeys
        val next = cur.copy(maxDisplacement = maxOf(cur.maxDisplacement, disp), distinctKeys = distinct)
        if (cur.owner == Owner.PENDING) {
            // 跨键滑动 + 超过抖动阈值才升级
            if (distinct >= minDistinctKeys && disp >= slopPx) {
                _state = next.copy(owner = Owner.GLIDE)
                return Decision.COLLECT
            }
            _state = next
            return Decision.COLLECT
        }
        if (cur.owner == Owner.GLIDE) {
            _state = next
            return Decision.COLLECT
        }
        return Decision.COLLECT
    }

    /** 长按计时到点：只有仍为 PENDING（未升级 Glide）才允许长按。 */
    fun onLongPressTimeout(): Decision {
        val cur = _state
        if (cur.owner == Owner.PENDING) {
            _state = cur.copy(owner = Owner.LONG_PRESS)
            return Decision.EMIT_LONG_PRESS
        }
        return Decision.COLLECT
    }

    fun onUp(pointerId: Long): Decision {
        val cur = _state
        if (cur.owner == Owner.CANCELLED) { _state = State(); return Decision.DISCARD }
        if (cur.pointerId != pointerId) return Decision.COLLECT
        return when (cur.owner) {
            Owner.GLIDE -> { _state = State(); Decision.EMIT_GLIDE }
            Owner.LONG_PRESS -> { _state = State(); Decision.COLLECT } // 长按动作已在 timeout 时执行
            Owner.PENDING -> { _state = State(); Decision.EMIT_TAP }
            else -> { _state = State(); Decision.COLLECT }
        }
    }

    /** 系统取消 / 切页 / 方案切换：保留 CANCELLED 直到下一次 DOWN，不补点击。 */
    fun onCancel(): Decision {
        _state = _state.copy(owner = Owner.CANCELLED)
        return Decision.DISCARD
    }

    /**
     * UI 在"取消拖动 / 协程异常结束"等兜底补点击前调用。
     *
     * 语义是"glide 是否占用了这次手势"：只有 GLIDE（或抬手前的 LONG_PRESS）
     * 才拦截补点击。NONE（功能关闭/起点非字母）表示 glide 未接管，
     * 由原有点击链路自行决定；CANCELLED 同样必须拦截（系统取消不补字）。
     */
    fun allowFallbackClick(): Boolean = when (_state.owner) {
        Owner.GLIDE, Owner.LONG_PRESS, Owner.CANCELLED -> false
        else -> true
    }
}
