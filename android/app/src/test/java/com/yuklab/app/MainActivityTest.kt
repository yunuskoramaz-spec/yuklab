package com.yuklab.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    private fun texts(v:View):List<TextView> = (if(v is TextView) listOf(v) else emptyList()) + if(v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private fun tap(root:View,label:String) { val view=texts(root).first { it.text.toString()==label }; if(view.isClickable) view.performClick() else (view.parent as View).performClick(); shadowOf(Looper.getMainLooper()).idle() }
    private fun capture(view:View,name:String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1746,View.MeasureSpec.EXACTLY)); view.layout(0,0,786,1746)
        val bitmap=Bitmap.createBitmap(786,1746,Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
        val file=File("build/previews/$name.png"); file.parentFile.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun guestCanExploreDemoListingsAndNavigateWithoutAuthentication() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup(); val a=controller.get(); var root=a.window.decorView
            assertTrue(texts(root).any { it.text.toString()=="Örnek ekranları incele" })
            capture(root,"home")
            tap(root,"Örnek ekranları incele"); root=a.window.decorView
            assertTrue(texts(root).any { it.text.toString().contains("TÜM KAYITLAR ÖRNEKTİR") })
            tap(root,"İlanlar"); root=a.window.decorView
            assertTrue(texts(root).any { it.text.toString()=="Kayseri" }); capture(root,"listings")
            tap(root,"Profil"); root=a.window.decorView; tap(root,"Taşıyıcı görünümüne geç"); root=a.window.decorView
            assertTrue(texts(root).any { it.text.toString()=="TAŞIYICI MERKEZİ" }); capture(root,"provider-home")
        }
    }
    @Test fun costCalculatorIsAccessibleWithoutAnAccount() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup(); val a=controller.get(); tap(a.window.decorView,"Sefer maliyeti")
            assertTrue(texts(a.window.decorView).any { it.text.toString()=="Maliyeti hesapla" })
            assertFalse(texts(a.window.decorView).any { it.text.toString()=="Giriş yap" })
        }
    }
}
