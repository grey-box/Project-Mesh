package com.greybox.projectmesh

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.asInetAddress
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.randomApipaAddr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import java.time.Duration

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
        val deviceNameMap = HashMap<String, String?>()

        // Helper method to add/update a device name
        fun addDevice(ipAddress: String, name: String?) {
            deviceNameMap[ipAddress] = name
        }

        fun removeDevice(ipAddress: String) {
            deviceNameMap.remove(ipAddress)
        }

        // Helper method to get a device name by IP
        fun getDeviceName(ipAddress: String): String? {
            return deviceNameMap[ipAddress]
        }
    }
    private val diModule = DI.Module("project_mesh") {
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
        bind <Json>() with singleton {
            Json {
                encodeDefaults = true
            }
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
                .connectTimeout(Duration.ofSeconds(30))
                // The maximum time to wait for data to be read from the server
                .readTimeout(Duration.ofSeconds(30))
                // The maximum time to wait for data to be written to the server
                .writeTimeout(Duration.ofSeconds(30))
                .build()
        }

        bind<AppServer>() with singleton {
            val node: AndroidVirtualNode = instance()
            AppServer(
                appContext = applicationContext,
                httpClient = instance(),
                port = AppServer.DEFAULT_PORT,
                name = node.addressAsInt.addressToDotNotation(),
                localVirtualAddr = node.address,
                receiveDir = instance(tag = TAG_RECEIVE_DIR),
                json = instance()
            )
        }

        onReady {
            instance<AppServer>().start()
            Log.d("AppServer", "Server started successfully on port: ${AppServer.DEFAULT_PORT}")
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
    }
}