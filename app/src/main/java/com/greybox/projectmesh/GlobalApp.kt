package com.greybox.projectmesh

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.extension.deviceInfo
import com.greybox.projectmesh.extension.networkDataStore
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.asInetAddress
import com.ustadmobile.meshrabiya.log.MNetLogger
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.randomApipaAddr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import java.io.File
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/*
initialize global variables and DI(dependency injection) container
why use DI?
All dependencies are defined in one place, which makes it easier to manage and test.
 */
class GlobalApp : Application(), DIAware {
    // it is an instance of Preferences.key<Int>, used to interact with "DataStore"
    private val addressKey = intPreferencesKey("virtual_node_address")
    data object DeviceInfoManager {
        // Global HashMap to store IP-DeviceName mapping
        private val deviceNameMap = ConcurrentHashMap<String, String?>()

        // Helper method to add/update a device name
        fun addDevice(ipAddress: String, name: String?) {
            deviceNameMap[ipAddress] = name
        }

        fun removeDevice(ipAddress: String) {
            deviceNameMap.remove(ipAddress)
        }

        fun getDeviceName(inetAddress: String): String? {
            return deviceNameMap[inetAddress]
        }

        fun getDeviceName(inetAddress: InetAddress): String? {
            return deviceNameMap[inetAddress.hostAddress]
        }

        fun getChatName(inetAddress: InetAddress): String {
            return inetAddress.hostAddress
        }
    }
    @SuppressLint("SimpleDateFormat")
    private val diModule = DI.Module("project_mesh") {
        // CoroutineScope for background initialization
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // create a single instance of "InetAddress" for the entire lifetime of the application
        bind<InetAddress>(tag=TAG_VIRTUAL_ADDRESS) with singleton {
            // fetch an IP address from the data store or generate a random one
            // Run a coroutine in a blocking way, it will block the main thread
            runBlocking {
                // fetch the address from the data store
                val address = applicationContext.networkDataStore.data.map { preference ->
                    preference[addressKey] ?: 0
                }.first()

                // if the address is not 0, converted to an IP address
                if(address != 0) {
                    address.asInetAddress()
                }
                else{
                    // if not, generate a random one,
                    // store it in the data store and converted to IP address
                    randomApipaAddr().also {
                            randomAddress -> applicationContext.networkDataStore.edit {
                        // "it" used to access the 'Preferences' object
                        it[addressKey] = randomAddress
                    }
                    }.asInetAddress()
                }
            }
        }
        bind<MNetLogger>() with singleton {
            val logFileNameDateComp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val logDir: File = instance(tag = TAG_LOG_DIR)
            MNetLoggerAndroid(
                deviceInfo = deviceInfo(),
                minLogLevel = Log.DEBUG,
                logFile = File(logDir, "${logFileNameDateComp}_${Build.MANUFACTURER}_${Build.MODEL}.log")
            )
        }
        bind <Json>() with singleton {
            Json {
                encodeDefaults = true
            }
        }

        bind<File>(tag = TAG_LOG_DIR) with singleton {
            File(filesDir, "log")
        }

        /*
        Ensuring a directory named "www" was created
        */
        bind<File>(tag = TAG_WWW_DIR) with singleton {
            File(filesDir, "www").also{
                if(!it.exists()) {
                    it.mkdirs()
                }
            }
        }

        bind<File>(tag = TAG_RECEIVE_DIR) with singleton {
            File(filesDir, "receive")
        }

        bind<AndroidVirtualNode>() with singleton {
            // initialize the AndroidVirtualNode Constructor
            AndroidVirtualNode(
                appContext = applicationContext,
                logger = instance(),
                json = instance(),
                // inject the "InetAddress" instance
                address = instance(tag = TAG_VIRTUAL_ADDRESS),
                dataStore = applicationContext.networkDataStore
            )
        }
        // The OkHttpClient will be created only once and shared across the app when needed
        bind<OkHttpClient>() with singleton {
            val node: AndroidVirtualNode = instance()
            OkHttpClient.Builder()
                .socketFactory(node.socketFactory)
                // The maximum time to wait for a connection to be established
                .connectTimeout(Duration.ofSeconds(20))
                // The maximum time to wait for data to be read from the server
                .readTimeout(Duration.ofSeconds(20))
                // The maximum time to wait for data to be written to the server
                .writeTimeout(Duration.ofSeconds(20))
                .build()
        }

        bind<MeshDatabase>() with singleton {
            // Lazy initialization – the database will be built when first accessed.
            Room.databaseBuilder(applicationContext,
                MeshDatabase::class.java,
                "mesh-database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        bind<SharedPreferences>(tag = "settings") with singleton {
            applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }

        bind<AppServer>() with singleton {
            val node: AndroidVirtualNode = instance()
            AppServer(
                appContext = applicationContext,
                httpClient = instance(),
                mLogger = instance(),
                port = AppServer.DEFAULT_PORT,
                name = node.addressAsInt.addressToDotNotation(),
                localVirtualAddr = node.address,
                receiveDir = instance(tag = TAG_RECEIVE_DIR),
                json = instance(),
                di = di,
                db = instance() // This will use the lazy database instance.
            )
        }

        onReady {
            appScope.launch {
                val logger: MNetLogger = instance()
                instance<AppServer>().start()
                logger(Log.DEBUG,"AppServer started successfully on Port: ${AppServer.DEFAULT_PORT}")
                instance<MeshDatabase>().messageDao().clearTable()
                logger(Log.DEBUG, "Database cleanup complete")
            }
        }
    }

    // DI container and its bindings are only set up when they are first needed
    override val di: DI by DI.lazy {
        import(diModule)
    }

    companion object {
        const val TAG_VIRTUAL_ADDRESS = "virtual_address"
        const val TAG_RECEIVE_DIR = "receive_dir"
        const val TAG_WWW_DIR = "www_dir"
        const val TAG_LOG_DIR = "log_dir"
    }
}