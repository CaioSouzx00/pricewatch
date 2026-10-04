package com.example.app.scraping

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Executa [PriceTrackingService.refreshAll] periodicamente. O rate limiting por host fica
 * no fetcher; aqui garantimos que nunca há dois ciclos simultâneos (loop sequencial).
 */
class ScrapingJob(
    private val service: PriceTrackingService,
    private val interval: Duration = 60.minutes,
    private val initialDelay: Duration = 30.seconds,
) {
    private val log = LoggerFactory.getLogger(ScrapingJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        log.info("Job de scraping iniciado (intervalo={}, atraso inicial={})", interval, initialDelay)
        job = scope.launch {
            delay(initialDelay)
            while (isActive) {
                runOnce()
                delay(interval)
            }
        }
    }

    suspend fun runOnce() {
        val started = System.currentTimeMillis()
        try {
            val s = service.refreshAll()
            log.info(
                "Ciclo de scraping concluído em {} ms: total={}, sucesso={}, falha={}, ignorados={}",
                System.currentTimeMillis() - started, s.total, s.succeeded, s.failed, s.skipped,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Ciclo de scraping falhou", e)
        }
    }

    fun stop() {
        scope.cancel()
        log.info("Job de scraping parado")
    }

    companion object {
        /** Habilitado por `SCRAPER_JOB_ENABLED=true`; intervalo em `SCRAPER_INTERVAL_MINUTES` (padrão 60, mín. 1). */
        fun fromEnv(service: PriceTrackingService): ScrapingJob? {
            if (System.getenv("SCRAPER_JOB_ENABLED")?.lowercase() != "true") return null
            val minutes = System.getenv("SCRAPER_INTERVAL_MINUTES")?.toLongOrNull()?.coerceAtLeast(1) ?: 60L
            return ScrapingJob(service, interval = minutes.minutes)
        }
    }
}
