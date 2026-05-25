package com.group4.chatapp.configs

import com.group4.chatapp.interceptors.WebSocketAuthenticationInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer


/**
 * Cấu hình WebSocket/STOMP cho tính năng realtime của ứng dụng.
 *
 * Class này thiết lập broker relay, endpoint kết nối và interceptor xác thực
 * cho inbound WebSocket message.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig
@Autowired constructor(

    val webSocketInterceptor: WebSocketAuthenticationInterceptor,

    @Value($$"${websocket.relay_host}") val relayHost: String,
    @Value($$"${websocket.relay_port}") val relayPort: Int,

) : WebSocketMessageBrokerConfigurer {

    /**
     * Cấu hình broker cho các topic/queue và application prefix.
     *
     * Behavior của method:
     * - Kích hoạt STOMP broker relay cho `/topic` và `/queue`.
     * - Dùng relay host/port từ cấu hình ứng dụng.
     * - Đặt prefix `/app` cho destination gửi vào application.
     *
     * @param registry Registry cấu hình message broker.
     */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {

        registry
            .enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost(relayHost)
            .setRelayPort(relayPort)

        registry.setApplicationDestinationPrefixes("/app")
    }

    /**
     * Đăng ký các endpoint WebSocket để client kết nối.
     *
     * Behavior của method:
     * - Cung cấp endpoint `/socket` có hỗ trợ SockJS.
     * - Cung cấp endpoint `/ws` cho kết nối WebSocket trực tiếp.
     * - Cho phép origin bất kỳ theo cấu hình hiện tại.
     *
     * @param registry Registry đăng ký stomp endpoints.
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {

        registry.addEndpoint("/socket")
            .setAllowedOriginPatterns("*")
            .withSockJS()

        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }

    /**
     * Gắn interceptor xác thực vào kênh inbound của WebSocket.
     *
     * Behavior của method:
     * - Mỗi message đi vào hệ thống sẽ đi qua interceptor trước khi xử lý.
     * - Dùng để kiểm tra danh tính và trạng thái xác thực của kết nối WebSocket.
     *
     * @param registration Cấu hình channel registration.
     */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketInterceptor)
    }
}