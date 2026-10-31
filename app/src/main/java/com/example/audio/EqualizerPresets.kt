package com.example.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/** Approximate curves in millibels. Interpolation adapts to the bands exposed by each device. */
enum class EqualizerPreset(val levelsMb: List<Int>) {
  FLAT(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
  BASS_BOOST(listOf(550, 500, 400, 250, 100, 0, 0, 0, 0, 0)),
  BASS_REDUCER(listOf(-500, -450, -350, -200, -100, 0, 0, 0, 0, 0)),
  TREBLE_BOOST(listOf(0, 0, 0, 0, 0, 50, 150, 250, 400, 500)),
  VOCAL(listOf(-200, -150, -100, 0, 200, 350, 400, 300, 100, 0)),
  CLASSICAL(listOf(250, 200, 100, 0, -100, -100, 0, 150, 250, 300)),
  DANCE(listOf(450, 400, 250, 50, 0, -100, 0, 200, 300, 350)),
  JAZZ(listOf(300, 250, 100, 100, -50, -50, 50, 150, 250, 300)),
  ROCK(listOf(400, 300, 150, -100, -200, -100, 100, 250, 350, 400)),
  ELECTRONIC(listOf(450, 400, 200, 0, -100, 50, 150, 250, 350, 400)),
  ACOUSTIC(listOf(250, 200, 50, 50, 150, 200, 150, 200, 250, 200)),
  POP(listOf(-50, 50, 150, 250, 300, 250, 150, 50, -50, -100));

  fun levelAt(frequencyHz: Int, minimumMb: Int, maximumMb: Int): Int {
    require(minimumMb <= maximumMb)
    val frequency = frequencyHz.coerceAtLeast(1).toDouble()
    val upper = FREQUENCIES.indexOfFirst { it >= frequency }.let { if (it < 0) FREQUENCIES.lastIndex else it }
    if (upper == 0 || frequency >= FREQUENCIES.last()) return levelsMb[upper].coerceIn(minimumMb, maximumMb)
    val lower = upper - 1
    val ratio = (ln(frequency) - ln(FREQUENCIES[lower])) / (ln(FREQUENCIES[upper]) - ln(FREQUENCIES[lower]))
    return (levelsMb[lower] + (levelsMb[upper] - levelsMb[lower]) * ratio)
      .roundToInt().coerceIn(minimumMb, maximumMb)
  }

  companion object {
    private val FREQUENCIES = listOf(32.0, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
  }
}
