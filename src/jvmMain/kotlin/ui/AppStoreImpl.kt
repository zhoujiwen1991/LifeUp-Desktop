package ui

import AppScope
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.runtime.*
import base.OkHttpClientHolder
import datasource.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import logger
import okhttp3.HttpUrl
import service.MdnsServiceDiscovery
import ui.text.Localization
import ui.text.StringText
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.prefs.Preferences

class AppStoreImpl(
    private val coroutineScope: CoroutineScope
) {

    val resourceDir
        get() = System.getenv("APPDATA").ifBlank {
            System.getProperty("compose.application.resources.dir")
        }.ifBlank {
            System.getProperty("user.dir")
        }


    val cacheDir
        get() = File(resourceDir + File.separator + "LifeUp Desktop" + File.separator + "cache").also {
            if (it.exists().not()) {
                it.mkdirs()
            }
        }

    private val apiService = ApiService.instance

    var strings = initStrings()
        private set


    var dialogStatus: DialogStatus? by mutableStateOf(null)
        private set


    var coinValue: Long? by mutableStateOf(null)
        private set

    val preferences = Preferences.userRoot()

    var ip by mutableStateOf(preferences.get("ip", ""))
    var port by mutableStateOf(preferences.get("port", "13276"))

    var isReadyToCall = false
        private set

    private val fetching = AtomicBoolean(false)
    private val mdnsServiceDiscovery = MdnsServiceDiscovery()


    private fun initStrings(): StringText {
        return Localization.get()
    }

    private var fetchCoin: Boolean = false
    private val fetchCoinFlow = flow {
        while (true) {
            if (fetchCoin) {
                kotlin.runCatching {
                    apiService.getCoin()
                }.onSuccess {
                    emit(it)
                }.onFailure {
                    emit(null)
                    logger.log(Level.SEVERE, "get coin error", it)
                }
            }
            kotlinx.coroutines.delay(1500)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    init {
        AppScope.launch {
            mdnsServiceDiscovery.register()
        }
        updateIpOrPort()
    }

    fun listServerInfo(): List<MdnsServiceDiscovery.IpAndPort> {
        return mdnsServiceDiscovery.ipAndPorts.values.toList().mapNotNull { it }
    }

    fun updateIpOrPort(ip: String = this.ip, port: String = this.port) {
        this.ip = ip
        this.port = port

        if (ip.isEmpty() || port.isEmpty()) {
            Preferences.userRoot().apply {
                put("ip", ip)
                put("port", port)
            }
            return
        }

        val validHost = kotlin.runCatching {
            HttpUrl.Builder().scheme("http").host(ip).port(port = port.toIntOrNull() ?: 13276).build()
        }.onFailure {
            logger.log(Level.SEVERE, "update ip or port error", it)
        }.isSuccess

        isReadyToCall = validHost
        if (isReadyToCall) {
            OkHttpClientHolder.updateHost(ip, port)
            Preferences.userRoot().apply {
                put("ip", ip)
                put("port", port)
            }
            fetchCoin()
        }
    }


    fun fetchCoin() {
        if (fetching.get()) {
            return
        }
        coroutineScope.launch {
            kotlin.runCatching {
                fetching.set(true)
                apiService.getCoin()
            }.onSuccess {
                coinValue = it
            }.onFailure {
                coinValue = null
                logger.log(Level.SEVERE, "get coin error", it)
            }
            fetching.set(false)
        }
    }


    fun showDialog(
        title: String,
        message: String = "",
        positiveButton: String = strings.yes,
        negativeButton: String = strings.cancel,
        negativeAction: () -> Unit = {
            dialogStatus = null
        },
        positiveAction: () -> Unit
    ) {
        dialogStatus = DialogStatus(
            title, message, positiveButton, negativeButton, positiveAction, negativeAction
        )
        MaterialTheme
    }
}

val AppStore = compositionLocalOf<AppStoreImpl> { error("AppStore error") }

val ScaffoldState = compositionLocalOf<ScaffoldState> { error("ScaffoldState error") }

val Strings: StringText
    @Composable
    get() = AppStore.current.strings