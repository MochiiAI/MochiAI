package com.mochi.agent

import android.app.Application
import com.mochi.agent.core.security.TokenStore
import com.mochi.agent.data.cloud.CloudApi
import com.mochi.agent.data.cloud.CloudWebSocketClient

/**
 * App container: initialises the secure token store, the cloud REST client and
 * the WebSocket channel, then keeps the channel alive for the process lifetime.
 */
class MochiApplication : Application() {

    lateinit var tokenStore: TokenStore
        private set

    lateinit var cloudApi: CloudApi
        private set

    lateinit var cloudWebSocket: CloudWebSocketClient
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        cloudApi = CloudApi(tokenStore)
        cloudWebSocket = CloudWebSocketClient(tokenStore, cloudApi)
        // Reconnect aggressively: the avatar depends on cloud state even when
        // the app is backgrounded.
        cloudWebSocket.connect()
    }
}