package com.yuklab.app

import java.math.BigDecimal
import java.math.RoundingMode

object Amounts {
    fun minor(value:String,positive:Boolean):String {
        try {
            val amount=BigDecimal(value.trim().replace(',','.'))
            require(if(positive) amount.signum()>0 else amount.signum()>=0)
            return amount.movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact().toString()
        } catch (_:Exception) { throw IllegalArgumentException("Geçerli bir tutar girin (en fazla 2 ondalık basamak).") }
    }
    fun display(minor:String):String=try { BigDecimal(minor).movePointLeft(2).setScale(2).toPlainString().replace('.',',') } catch (_:Exception) { "—" }
}
