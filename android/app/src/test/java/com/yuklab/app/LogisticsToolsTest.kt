package com.yuklab.app

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class LogisticsToolsTest {
    @Test fun costIncludesFuelDailyAndTollsWithoutRoundingLoss() {
        val c=LogisticsTools.cost(BigDecimal("1000"),BigDecimal("30"),BigDecimal("50"),BigDecimal("2"),BigDecimal("1000"),BigDecimal("500"),BigDecimal("100"))
        assertEquals(BigDecimal("15000.00"),c.fuel); assertEquals(BigDecimal("2600.00"),c.operations); assertEquals(BigDecimal("17600.00"),c.total)
    }
    @Test(expected=IllegalArgumentException::class) fun costRejectsNegativeInputs() { LogisticsTools.cost(BigDecimal(-1),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO) }
    @Test fun parsesTurkishGroupedCurrencyAndConvertsTonsToKg() {
        val result=LogisticsTools.parse("Kayseri → İzmir | 1,5 ton | 30.000 TL\nBursa -> Gaziantep | 500 kg | 2500,50 TL\nBelirsiz bir satır")
        assertEquals(2,result.drafts.size); assertEquals(1,result.rejected.size)
        assertEquals(1500.0,result.drafts[0].getDouble("weightKg"),0.0); assertEquals("30000.00",result.drafts[0].getString("budget")); assertEquals("2500.50",result.drafts[1].getString("budget"))
        assertEquals("İzmir",result.drafts[0].getString("deliveryCity"))
    }
    @Test fun limitsBulkImportAndDoesNotInventMissingWeights() {
        val result=LogisticsTools.parse((1..60).joinToString("\n") { "Kayseri → İzmir" }); assertEquals(50,result.drafts.size); assertFalse(result.drafts[0].has("weightKg")); assertFalse(result.drafts[0].has("budget"))
    }
    @Test fun searchesWithTurkishCaseRules() { assertTrue(LogisticsTools.contains("İZMİR","izmir")); assertTrue(LogisticsTools.contains("IĞDIR","ığdır")) }
}
