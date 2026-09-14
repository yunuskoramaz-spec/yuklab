package com.yuklab.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

object LogisticsTools {
    data class Cost(val fuel:BigDecimal,val operations:BigDecimal,val total:BigDecimal)
    fun cost(km:BigDecimal,litersPer100:BigDecimal,fuelPrice:BigDecimal,days:BigDecimal,dailyExpense:BigDecimal,tolls:BigDecimal,other:BigDecimal):Cost {
        require(listOf(km,litersPer100,fuelPrice,days,dailyExpense,tolls,other).all { it.signum()>=0 }) { "Değerler negatif olamaz." }
        val fuel=km.multiply(litersPer100).multiply(fuelPrice).divide(BigDecimal(100)).setScale(2,RoundingMode.HALF_UP)
        val operations=(days*dailyExpense+tolls+other).setScale(2,RoundingMode.HALF_UP)
        return Cost(fuel,operations,fuel+operations)
    }
    data class Parsed(val drafts:List<JSONObject>,val rejected:List<String>)
    fun parse(text:String):Parsed {
        val drafts=mutableListOf<JSONObject>(); val rejected=mutableListOf<String>()
        text.lines().filter { it.isNotBlank() }.take(50).forEach { line ->
            val parts=line.substringBefore('|').split(Regex("\\s*(?:→|->|–| - )\\s*"),limit=2)
            if(parts.size!=2 || parts.any { it.trim().isEmpty() || it.trim().length>100 }) { rejected.add(line); return@forEach }
            val draft=JSONObject().put("pickupCity",parts[0].trim()).put("deliveryCity",parts[1].trim()).put("serviceType","LOAD").put("note",line.take(1000))
            Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(ton|kg)\\b",RegexOption.IGNORE_CASE).find(line)?.let {
                val amount=it.groupValues[1].replace(',','.').toDoubleOrNull()
                if(amount!=null && amount>0 && amount.isFinite()) draft.put("weightKg",amount*if(it.groupValues[2].equals("ton",true)) 1000 else 1)
            }
            Regex("([0-9][0-9.,]*)\\s*(TL|₺)",RegexOption.IGNORE_CASE).find(line)?.let {
                val raw=it.groupValues[1]; val normalized=if(raw.matches(Regex("[0-9]{1,3}(\\.[0-9]{3})+(,[0-9]{1,2})?"))) raw.replace(".","") else raw
                try { draft.put("budget",BigDecimal(Amounts.minor(normalized,false)).movePointLeft(2).toPlainString()) } catch (_:Exception) {}
            }
            drafts.add(draft)
        }
        return Parsed(drafts,rejected)
    }
    fun contains(text:String,query:String)=text.lowercase(java.util.Locale.forLanguageTag("tr-TR")).contains(query.trim().lowercase(java.util.Locale.forLanguageTag("tr-TR")))
}
