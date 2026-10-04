package com.example.app.scraping

import com.example.app.alert.AlertEvaluator
import com.example.app.dto.ProductResponse
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class PriceTrackingService(
    private val scrapers: List<PriceScraper>,
    private val products: ProductRepository,
    private val history: PriceHistoryRepository,
    private val alertEvaluator: AlertEvaluator? = null,
) {
    private val log = LoggerFactory.getLogger(PriceTrackingService::class.java)

    /** Faz o scrape, grava em `price_history` e atualiza o preço atual. Lança [ScrapeException] em falhas. */
    suspend fun refresh(product: ProductResponse): ScrapeResult {
        val scraper = scrapers.firstOrNull { it.supports(product.url) }
            ?: throw ScrapeException.Parse("Loja não suportada")
        val result = scraper.scrape(product.url)
        history.add(product.id, result.price)
        products.updatePrice(product.id, result.price, result.inStock)
        log.info("Produto {} atualizado: {} {}", product.id, result.currency, result.price)
        try {
            // `product` ainda carrega o preço/estoque anteriores, usados para comparar.
            alertEvaluator?.evaluate(product, result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Falha ao avaliar alertas do produto {}", product.id, e)
        }
        return result
    }

    /** Atualiza todos os produtos ativos; falhas individuais não interrompem o lote. */
    suspend fun refreshAll(): RefreshSummary {
        val active = products.listActive()
        var ok = 0
        var failed = 0
        var skipped = 0
        for ((index, product) in active.withIndex()) {
            try {
                refresh(product)
                ok++
            } catch (e: ScrapeException.Blocked) {
                log.warn("Bloqueio detectado; interrompendo lote: {}", e.message)
                failed++
                skipped = active.size - index - 1
                break
            } catch (e: ScrapeException) {
                log.warn("Falha ao atualizar produto {}: {}", product.id, e.message)
                failed++
            }
        }
        return RefreshSummary(total = active.size, succeeded = ok, failed = failed, skipped = skipped)
    }
}

data class RefreshSummary(val total: Int, val succeeded: Int, val failed: Int, val skipped: Int)
