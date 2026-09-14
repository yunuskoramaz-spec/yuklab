package com.yuklab.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View

object Palette {
    val ink=Color.rgb(23,27,30)
    val accentText=Color.rgb(0,108,112)
    val muted=Color.rgb(89,102,108)
    val accent=Color.rgb(0,184,189)
    val background=Color.rgb(244,246,247)
    val line=Color.rgb(217,225,228)
    val soft=Color.rgb(224,246,245)
    fun shape(color:Int,radius:Float=20f,stroke:Int?=null)=GradientDrawable().apply {
        setColor(color); cornerRadius=radius
        if(stroke!=null) setStroke(1,stroke)
    }
}

/** Small vector icons, drawn at device density without external image downloads. */
class Glyph(context:Context,private val kind:String,private val tint:Int=Palette.ink):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=tint; style=Paint.Style.STROKE; strokeWidth=1.7f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas); canvas.save(); canvas.scale(width/24f,height/24f)
        fun line(vararg xy:Float) { val path=Path(); path.moveTo(xy[0],xy[1]); for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1]); canvas.drawPath(path,p) }
        when(kind) {
            "truck" -> { canvas.drawRoundRect(2f,6f,14f,16f,1f,1f,p); line(14f,10f,18f,10f,22f,14f,22f,18f,20f,18f); line(8f,18f,16f,18f); canvas.drawCircle(6f,18f,2f,p); canvas.drawCircle(18f,18f,2f,p) }
            "home" -> { line(3f,10f,12f,3f,21f,10f); line(5f,9f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,9f) }
            "user" -> { canvas.drawCircle(12f,7f,4f,p); canvas.drawArc(4f,13f,20f,28f,180f,180f,false,p) }
            "contacts" -> { canvas.drawRoundRect(3f,6f,21f,21f,3f,3f,p); line(8f,6f,8f,3f,16f,3f,16f,6f); line(9f,7f,9f,20f); line(15f,7f,15f,20f) }
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "arrow" -> { line(5f,12f,19f,12f,14f,7f); line(19f,12f,14f,17f) }
            "back" -> { line(19f,12f,5f,12f,10f,7f); line(5f,12f,10f,17f) }
            "check" -> line(5f,12f,10f,17f,20f,6f)
            "pin" -> { val q=Path(); q.moveTo(12f,22f); q.cubicTo(0f,11f,5f,2f,12f,2f); q.cubicTo(19f,2f,24f,11f,12f,22f); canvas.drawPath(q,p); canvas.drawCircle(12f,9f,2.5f,p) }
            "calculator" -> { canvas.drawRoundRect(4f,2f,20f,22f,2f,2f,p); canvas.drawRect(7f,5f,17f,9f,p); for(x in listOf(8f,12f,16f)) for(y in listOf(13f,17f)) canvas.drawPoint(x,y,p) }
            "box" -> { line(3f,6f,12f,2f,21f,6f,21f,18f,12f,22f,3f,18f,3f,6f,12f,10f,21f,6f); line(12f,10f,12f,22f); line(7f,4f,16f,8f) }
            else -> { canvas.drawRoundRect(5f,2f,19f,22f,2f,2f,p); line(8f,7f,16f,7f); line(8f,12f,16f,12f); line(8f,17f,14f,17f) }
        }
        canvas.restore()
    }
}
