package xyz.fireworkwars.data

import org.slf4j.LoggerFactory
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.RedisClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MessageBus(host: String, port: Int) {

    private val logger = LoggerFactory.getLogger(MessageBus::class.java)
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()

    private val client = RedisClient.builder()
        .hostAndPort(host, port)
        .poolConfig(ConnectionPoolConfig().apply { maxTotal = 16; maxIdle = 8 })
        .build()

    private lateinit var subscriber: ExecutorService
    private lateinit var pubSub: JedisPubSub

    @Volatile private var running = true

    fun subscribe(channel: String, handler: (String) -> Unit) {
        handlers.computeIfAbsent(channel) { CopyOnWriteArrayList() }.add(handler)
    }

    fun publish(channel: String, message: String) {
        client.publish(channel, message)
    }

    fun start() {
        pubSub = object : JedisPubSub() {
            override fun onMessage(channel: String, message: String) {
                handlers[channel]?.forEach { handler ->
                    try {
                        handler(message)
                    } catch (e: Exception) {
                        logger.warn("Redis handler for channel $channel threw", e)
                    }
                }
            }
        }

        subscriber = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "fww-redis-subscriber").apply { isDaemon = true }
        }

        subscriber.submit(::runSubscriberLoop)
    }

    private fun runSubscriberLoop() {
        var backoffMillis = INITIAL_BACKOFF_MILLIS

        while (running) {
            val channels = handlers.keys.toTypedArray()

            if (channels.isEmpty()) {
                Thread.sleep(POLL_MILLIS)
                continue
            }

            try {
                client.subscribe(pubSub, *channels)
                backoffMillis = INITIAL_BACKOFF_MILLIS
            } catch (e: Throwable) {
                if (!running) break

                logger.warn("Redis subscriber error, reconnecting in ${backoffMillis}ms", e)

                try {
                    Thread.sleep(backoffMillis)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }

                backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    fun close() {
        running = false

        if (::pubSub.isInitialized) {
            try { pubSub.unsubscribe() } catch (_: Throwable) {}
        }

        if (::subscriber.isInitialized) subscriber.shutdownNow()
        client.close()
    }

    companion object {
        private const val INITIAL_BACKOFF_MILLIS = 1_000L
        private const val MAX_BACKOFF_MILLIS = 30_000L
        private const val POLL_MILLIS = 200L
    }
}
