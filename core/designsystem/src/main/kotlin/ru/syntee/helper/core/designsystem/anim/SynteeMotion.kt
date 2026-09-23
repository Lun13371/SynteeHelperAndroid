package ru.syntee.helper.core.designsystem.anim

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Пресеты анимаций Syntee — аналог motionPresets десктопа.
 * Единая «физика» движения во всём приложении.
 */
object SynteeMotion {

    /** Пружина нажатия/hover — живая, но сдержанная. */
    fun <T> pressSpring(): AnimationSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = 600f,
    )

    /** Появление элементов (сообщения, карточки). */
    fun <T> appear(): AnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f,
    )

    /** Кросс-фейд для смены состояний. */
    fun <T> fade(): AnimationSpec<T> = tween(durationMillis = 180)
}
