package ai.realteeth.imagejobserver.global.config

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerProperties
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    @Bean
    fun mockWorkerWebClient(properties: MockWorkerProperties): WebClient {
        val timeout = Duration.ofMillis(properties.timeoutMs)

        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.timeoutMs.toInt())
            .responseTimeout(timeout)
            .doOnConnected {
                it.addHandlerLast(ReadTimeoutHandler(properties.timeoutMs, TimeUnit.MILLISECONDS))
                it.addHandlerLast(WriteTimeoutHandler(properties.timeoutMs, TimeUnit.MILLISECONDS))
            }

        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
