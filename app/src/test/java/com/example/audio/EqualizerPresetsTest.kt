package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetsTest {
  @Test fun allCurvesStayWithinTheDeviceRange() {
    EqualizerPreset.entries.forEach { preset ->
      listOf(0, 32, 40, 63, 125, 500, 1000, 4000, 12000, 16000, 24000).forEach { frequency ->
        assertTrue(preset.levelAt(frequency, -200, 200) in -200..200)
      }
    }
  }

  @Test fun presetsUseDeviceFrequenciesNotBandIndices() {
    assertEquals(550, EqualizerPreset.BASS_BOOST.levelAt(32, -1500, 1500))
    assertEquals(0, EqualizerPreset.BASS_BOOST.levelAt(8000, -1500, 1500))
    assertEquals(350, EqualizerPreset.VOCAL.levelAt(1000, -1500, 1500))
    assertTrue(EqualizerPreset.BASS_BOOST.levelAt(45, -1500, 1500) in 500..550)
    assertEquals(0, EqualizerPreset.FLAT.levelAt(2345, -1500, 1500))
  }
}
