package ian.dev.homey.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * 动效参数，取自 Material 3 motion 规范（emphasized easing）。
 * 页面切换统一用这里的时长和曲线，保持节奏一致。
 */
object Motion {
    /** 进入：快起慢停，适合新页面滑入。 */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退出：慢起快走，适合页面离开。 */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 普通状态变化（颜色、尺寸）。 */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val PAGE_ENTER_MS = 380
    const val PAGE_EXIT_MS = 260
    const val FADE_MS = 180
    const val STATE_MS = 220

    fun <T> enter(duration: Int = PAGE_ENTER_MS): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedDecelerate)
    fun <T> exit(duration: Int = PAGE_EXIT_MS): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedAccelerate)
    fun <T> state(duration: Int = STATE_MS): FiniteAnimationSpec<T> = tween(duration, easing = Standard)
}
