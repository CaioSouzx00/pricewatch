package com.example.app.scraping

import java.math.BigDecimal

object PriceStringParser {
    /**
     * Converte strings monetárias como "R$ 1.299,90", "1299.90", "R$ 59,99" em BigDecimal.
     * Retorna nulo se não for possível converter ou se o valor for zero/negativo.
     */
    fun parse(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        var clean = raw.replace("R$", "", ignoreCase = true)
            .replace("BRL", "", ignoreCase = true)
            .replace("\u00a0", " ")
            .trim()

        if (clean.contains(",") && clean.contains(".")) {
            // Padrão brasileiro: "1.299,90"
            clean = clean.replace(".", "").replace(",", ".")
        } else if (clean.contains(",")) {
            // Padrão com vírgula: "1299,90"
            clean = clean.replace(",", ".")
        }

        clean = clean.replace(Regex("[^0-9.]"), "")
        return clean.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    }
}
