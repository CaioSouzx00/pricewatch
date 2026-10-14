package com.example.app.alert

import com.example.app.dto.AlertResponse
import com.example.app.dto.AlertType
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertRulesTest {
    private fun alert(type: AlertType, target: String? = null, drop: String? = null) = AlertResponse(
        id = 1,
        userId = 1,
        productId = 1,
        productName = "Produto Teste",
        productImageUrl = "https://example.com/img.jpg",
        type = type,
        targetPrice = target?.let { BigDecimal(it) },
        percentageDrop = drop?.let { BigDecimal(it) },
        active = true,
        triggeredAt = null,
        createdAt = Instant.now(),
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
    fun `calculo de porcentagem de queda nao lanca ArithmeticException em dizimas periodicas`() {
        // 300 -> 200 = 100/300 = 33.3333...% (dízima periódica que estourava ArithmeticException)
        val drop1 = AlertMath.calculatePercentageDrop(BigDecimal("300.00"), BigDecimal("200.00"))
        assertEquals(BigDecimal("33.3333"), drop1)

        // 10 -> 3 = 70%
        val drop2 = AlertMath.calculatePercentageDrop(BigDecimal("10.00"), BigDecimal("3.00"))
        assertEquals(BigDecimal("70.0000"), drop2)

        // 7 -> 2 = 5/7 = 71.4285714...%
        val drop3 = AlertMath.calculatePercentageDrop(BigDecimal("7.00"), BigDecimal("2.00"))
        assertEquals(BigDecimal("71.4286"), drop3)
    }

    @Test
    fun `calculo de porcentagem de queda trata precos nulos, zero e negativos sem excecao`() {
        assertNull(AlertMath.calculatePercentageDrop(null, BigDecimal("50.00")))
        assertNull(AlertMath.calculatePercentageDrop(BigDecimal.ZERO, BigDecimal("50.00")))
        assertNull(AlertMath.calculatePercentageDrop(BigDecimal("-10.00"), BigDecimal("50.00")))
        assertEquals(BigDecimal.ZERO, AlertMath.calculatePercentageDrop(BigDecimal("100.00"), BigDecimal("100.00")))
        assertEquals(BigDecimal.ZERO, AlertMath.calculatePercentageDrop(BigDecimal("100.00"), BigDecimal("150.00")))
    }

    @Test
    fun `alerta de queda percentual avalia corretamente o gatilho`() {
        val alert = alert(AlertType.PERCENTAGE_DROP, drop = "15.00") // Alerta de 15% de queda

        // Queda de 10% (100 -> 90): não dispara
        assertFalse(AlertRules.matches(alert, BigDecimal("100.00"), BigDecimal("90.00"), null, true))

        // Queda de 15% (100 -> 85): dispara
        assertTrue(AlertRules.matches(alert, BigDecimal("100.00"), BigDecimal("85.00"), null, true))

        // Queda de 33.33% (300 -> 200): dispara sem estourar dízima
        assertTrue(AlertRules.matches(alert, BigDecimal("300.00"), BigDecimal("200.00"), null, true))

        // Preço anterior nulo ou zero: não dispara
        assertFalse(AlertRules.matches(alert, null, BigDecimal("80.00"), null, true))
        assertFalse(AlertRules.matches(alert, BigDecimal.ZERO, BigDecimal("80.00"), null, true))
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
