package dev.piko.app.settings

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkinSettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun store(name: String) = SettingsStore(
        context.getSharedPreferences(name, Context.MODE_PRIVATE),
        context.getSharedPreferences("$name-secret", Context.MODE_PRIVATE),
    )

    @Test fun `skin defaults to piko`() {
        assertEquals(SkinId.PIKO, store("skin-default").currentSkin)
    }

    @Test fun `legacy xiaoman value migrates to piko`() {
        val name = "skin-legacy"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("ui.skin", "XIAOMAN").commit()

        assertEquals(SkinId.PIKO, store(name).currentSkin)
        assertEquals("PIKO", prefs.getString("ui.skin", null))
    }

    @Test fun `piko value is read as is`() {
        val name = "skin-piko"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("ui.skin", "PIKO").commit()

        assertEquals(SkinId.PIKO, store(name).currentSkin)
        assertEquals("PIKO", prefs.getString("ui.skin", null))
    }

    @Test fun `saved skin survives a new store instance`() {
        val name = "skin-persist"
        val first = store(name)
        first.saveSkin(SkinId.GITHUB)
        assertEquals(SkinId.GITHUB, first.skin.value)
        assertEquals(SkinId.GITHUB, store(name).currentSkin)
    }

    @Test fun `unknown stored value falls back to default`() {
        val name = "skin-invalid"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("ui.skin", "NOT_A_SKIN").commit()

        assertEquals(SkinId.PIKO, store(name).currentSkin)
        assertEquals("NOT_A_SKIN", prefs.getString("ui.skin", null))
    }

    @Test fun `saving model settings does not reset the skin`() {
        val name = "skin-survives-save"
        val store = store(name)
        store.saveSkin(SkinId.PIXEL)
        store.save(AppSettings())
        assertEquals(SkinId.PIXEL, store(name).currentSkin)
    }
}
