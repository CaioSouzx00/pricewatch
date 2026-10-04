package com.example.app.alert

import com.example.app.dto.AlertResponse
import com.example.app.dto.AlertType
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertRulesTest {
    private fun alert(type: AlertType, target: String? = null) = AlertResponse(
        1, 1, 1, type, target?.let { BigDecimal(it) }, true, null, Instant.now(),
    )

    @Test
    fun `preco abaixo dispara quando atinge o alvo`() {
        val a = alert(AlertType.PRICE_BELOW, "100.00")
        assertTrue(AlertRules.matches(a, null, BigDecimal("99.90"), null, true))
        assertTrue(AlertRules.matches(a, null, BigDecimal("100.00"), null, true))
        assertFalse(AlertRules.matches(a, null, BigDecimal("100.01"), null, true))
    }

    @Test
    fun `preco subiu exige preco anterior`() {
        val a = alert(AlertType.PRICE_UP)
        assertFalse(AlertRules.matches(a, null, BigDecimal("10"), null, true))
        assertTrue(AlertRules.matches(a, BigDecimal("10"), BigDecimal("11"), null, true))
        assertFalse(AlertRules.matches(a, BigDecimal("10"), BigDecimal("10"), null, true))
        assertFalse(AlertRules.matches(a, BigDecimal("10"), BigDecimal("9"), null, true))
    }

    @Test
    fun `mudanca de estoque so dispara quando muda`() {
        val a = alert(AlertType.STOCK_CHANGE)
        assertFalse(AlertRules.matches(a, null, BigDecimal.ONE, null, true))
        assertFalse(AlertRules.matches(a, null, BigDecimal.ONE, true, true))
        assertTrue(AlertRules.matches(a, null, BigDecimal.ONE, true, false))
        assertTrue(AlertRules.matches(a, null, BigDecimal.ONE, false, true))
    }

    @Test
    fun `corpo do Novu contem workflow, destinatario e payload`() {
        val body = NovuNotifier("k", workflowId = "wf").buildBody(
            AlertNotification(
                7, "a@b.com", "Ana", 1, AlertType.PRICE_UP, "Fone", "https://x", "BRL",
                null, BigDecimal("12.50"), true, "msg",
            ),
        ).toString()
        assertTrue(body.contains("\"name\":\"wf\""))
        assertTrue(body.contains("\"subscriberId\":\"7\""))
        assertTrue(body.contains("\"email\":\"a@b.com\""))
        assertTrue(body.contains("\"newPrice\":\"12.50\""))
        assertEquals(1, Regex("\"oldPrice\":null").findAll(body).count())
    }
}
