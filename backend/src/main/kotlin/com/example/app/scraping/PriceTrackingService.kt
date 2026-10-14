package com.example.app.scraping

import com.example.app.alert.AlertEvaluator
import com.example.app.dto.ProductResponse
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.ProductRepository
import com.example.app.repository.ScrapeLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class PriceTrackingService(
    val registry: PriceScraperRegistry,
    private val products: ProductRepository,
    private val history: PriceHistoryRepository,
    private val scrapeLogs: ScrapeLogRepository? = null,
    private val alertEvaluator: AlertEvaluator? = null,
) {
    constructor(
        scrapers: List<PriceScraper>,
        products: ProductRepository,
        history: PriceHistoryRepository,
        scrapeLogs: ScrapeLogRepository? = null,
        alertEvaluator: AlertEvaluator? = null,
    ) : this(PriceScraperRegistry(scrapers), products, history, scrapeLogs, alertEvaluator)

    private val log = LoggerFactory.getLogger(PriceTrackingService::class.java)

    // Limite de concorrência por domínio (max 2 conexões simultâneas por host)
    private val domainSemaphores = ConcurrentHashMap<String, Semaphore>()

    // Conjunto de produtos atualmente em processamento (evita sobreposição)
    private val inFlightProductIds = ConcurrentHashMap.newKeySet<Long>()

    private fun getDomainSemaphore(url: String): Semaphore {
        val host = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull() ?: "default"
        return domainSemaphores.computeIfAbsent(host) { Semaphore(2) }
    }

    /** Faz o scrape, grava em `price_history` e atualiza o preço atual e imagem. Lança [ScrapeException] em falhas. */
    suspend fun refresh(product: ProductResponse): ScrapeResult {
        if (!inFlightProductIds.add(product.id)) {
            log.warn("Produto {} já está em execução de coleta. Ignorando sobreposição.", product.id)
            throw ScrapeException.Parse("Coleta do produto ${product.id} já em andamento")
        }

        try {
            val scraper = registry.findScraper(product.url)
                ?: throw ScrapeException.Parse("Loja não suportada para URL: ${product.url}")

            val semaphore = getDomainSemaphore(product.url)
            val result = semaphore.withPermit {
                scraper.scrape(product.url)
            }

            history.add(product.id, result.price)
            products.updateScrapedData(product.id, result.price, result.inStock, result.imageUrl)
            log.info("Produto {} ({}) atualizado: {} {}", product.id, scraper.store, result.currency, result.price)

            try {
                // `product` ainda carrega o preço/estoque anteriores, usados para comparar.
                alertEvaluator?.evaluate(product, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Falha ao avaliar alertas do produto {}", product.id, e)
            }
            return result
        } finally {
            inFlightProductIds.remove(product.id)
        }
    }

    /**
     * Atualiza produtos com coleta vencida de acordo com o intervalo individual de cada produto.
     * Quando houver bloqueio (403/429/captcha), registra no log como "blocked" e segue com os demais.
     */
    suspend fun refreshAll(): RefreshSummary {
        val dueProducts = products.listDueForCheck()
        var ok = 0
        var failed = 0
        var blocked = 0

        for (product in dueProducts) {
            val start = System.currentTimeMillis()
            val detectedStore = product.store ?: registry.findScraper(product.url)?.store
            try {
                refresh(product)
                val latency = (System.currentTimeMillis() - start).toInt()
                scrapeLogs?.add(product.id, success = true, errorMessage = null, latencyMs = latency, store = detectedStore, blocked = false)
                ok++
            } catch (e: ScrapeException.Blocked) {
                val latency = (System.currentTimeMillis() - start).toInt()
                log.warn("Bloqueio detectado para o produto {} ({}); registrando e prosseguindo: {}", product.id, detectedStore, e.message)
                products.markChecked(product.id)
                scrapeLogs?.add(product.id, success = false, errorMessage = e.message, latencyMs = latency, store = detectedStore, blocked = true)
                failed++
                blocked++
            } catch (e: ScrapeException) {
                val latency = (System.currentTimeMillis() - start).toInt()
                log.warn("Falha ao atualizar produto {} ({}): {}", product.id, detectedStore, e.message)
                products.markChecked(product.id)
                scrapeLogs?.add(product.id, success = false, errorMessage = e.message, latencyMs = latency, store = detectedStore, blocked = false)
                failed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latency = (System.currentTimeMillis() - start).toInt()
                log.error("Erro inesperado no produto {}: {}", product.id, e.message)
                products.markChecked(product.id)
                scrapeLogs?.add(product.id, success = false, errorMessage = e.message, latencyMs = latency, store = detectedStore, blocked = false)
                failed++
            }
        }
        return RefreshSummary(total = dueProducts.size, succeeded = ok, failed = failed, skipped = blocked)
    }
}

data class RefreshSummary(val total: Int, val succeeded: Int, val failed: Int, val skipped: Int)
