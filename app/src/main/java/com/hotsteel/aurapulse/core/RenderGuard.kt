package com.hotsteel.aurapulse.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-way valve for decorative animation.
 *
 * Some GPUs (and some vendor render stacks) fall over on effects that are legal
 * on paper: a rotating layer over a large surface, a shadow around a generic
 * outline, a full-screen gradient rebuilt per frame. When that happens the main
 * thread blocks inside `HardwareRenderer.nSyncAndDrawFrame` and the app looks
 * frozen even though nothing crashed.
 *
 * The watchdog flips this flag the first time it sees a multi-second stall. Every
 * ambient animation reads it and parks itself on the next frame: the interface
 * keeps working, it simply stops moving.
 *
 * The flag survives a restart, because the user's only escape from a wedged
 * render thread is killing the app. It expires after a day so a later build gets
 * a fresh chance to animate.
 */
object RenderGuard {
  private const val PREFS = "aurapulse_render"
  private const val KEY_CALM = "calm"
  private const val KEY_AT = "calm_at"
  private const val EXPIRY_MS = 24L * 60 * 60 * 1000

  private var prefs: android.content.SharedPreferences? = null
  private val mutable = MutableStateFlow(false)
  val calm: StateFlow<Boolean> = mutable.asStateFlow()

  val isCalm: Boolean get() = mutable.value

  fun install(context: Context) {
    val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs = store
    val stamped = store.getLong(KEY_AT, 0L)
    val fresh = System.currentTimeMillis() - stamped < EXPIRY_MS
    mutable.value = store.getBoolean(KEY_CALM, false) && fresh
  }

  fun calmDown() {
    if (mutable.value) return
    mutable.value = true
    runCatching {
      prefs?.edit()
        ?.putBoolean(KEY_CALM, true)
        ?.putLong(KEY_AT, System.currentTimeMillis())
        ?.apply()
    }
  }
}
