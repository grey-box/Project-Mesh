package com.greybox.projectmesh

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.extension.deviceInfo
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.extension.networkDataStore
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.asInetAddress
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.randomApipaAddr
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
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
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.greybox.projectmesh.user.UserRepository
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.utils.MessageMigrationUtils
import com.greybox.projectmesh.testing.TestDeviceService
import com.greybox.projectmesh.user.UserEntity
import com.ustadmobile.meshrabiya.log.MNetLogger
import java.text.SimpleDateFormat
import java.util.Date

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
    object GlobalUserRepo {
        // Lateinit or lazy property
        lateinit var userRepository: UserRepository
        lateinit var prefs: SharedPreferences
        lateinit var conversationRepository: ConversationRepository
    }
    override fun onCreate() {
        super.onCreate()
        val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
        val uuid = sharedPrefs.getString("UUID", null)
        if (uuid == null) {
            // Generate a new UUID if one doesn't exist
            val newUuid = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit().putString("UUID", newUuid).apply()
            Log.d("GlobalApp", "Generated new UUID: $newUuid")
        }

        //get the repositories from DI
        val repo: UserRepository by di.instance()
        GlobalUserRepo.userRepository = repo
        GlobalUserRepo.prefs = sharedPrefs

        val convRepo: ConversationRepository by di.instance()
        GlobalUserRepo.conversationRepository = convRepo

        //initialize deviceStatus Manager:
        val appServer: AppServer by di.instance()
        DeviceStatusManager.initialize(appServer)

        //version checking migrating messages
        val hasMigratedMessages = sharedPrefs.getBoolean("has_migrated_messages", false)
        if (!hasMigratedMessages) {
            GlobalScope.launch {
                try {
                    Log.d("GlobalApp", "Starting message migration...")
                    val migrationUtils = MessageMigrationUtils(di)
                    migrationUtils.migrateMessagesToChatIds()
                    // Mark migration as complete
                    sharedPrefs.edit().putBoolean("has_migrated_messages", true).apply()
                    Log.d("GlobalApp", "Message migration completed and marked as done")
                } catch (e: Exception) {
                    Log.e("GlobalApp", "Error during message migration", e)
                    // Don't mark as complete if there was an error
                }
            }
        } else {
            Log.d("GlobalApp", "Message migration already performed, skipping")
        }

        //add test convos
        insertTestConversations()
    }

    fun insertTestConversations() {
        GlobalScope.launch {
            try {
                //get database instance
                val db: MeshDatabase by di.instance()

                // Check if any messages exist first
                val existingMessages = db.messageDao().getAll()
                Log.d("GlobalApp", "Found ${existingMessages.size} existing messages")

                if (existingMessages.isEmpty()) {
                    Log.d("GlobalApp", "No messages found, creating test messages...")

                    val localUuid = GlobalUserRepo.prefs.getString("UUID", null) ?: "local-user"

                    //insert test convo with online test device
                    val testDevice = TestDeviceService.getTestDeviceAddress()
                    val testUser = UserEntity(
                        uuid = "test-device-uuid",
                        name = TestDeviceService.TEST_DEVICE_NAME,
                        address = testDevice.hostAddress
                    )

                    //make sure the test user exists in the database
                    GlobalUserRepo.userRepository.insertOrUpdateUser(
                        testUser.uuid,
                        testUser.name,
                        testUser.address
                    )

                    //create convo with the test device
                    val onlineConversation =
                        GlobalUserRepo.conversationRepository.getOrCreateConversation(
                            localUuid = localUuid,
                            remoteUser = testUser
                        )

                    //create online test message
                    val onlineTestMessage = Message(
                        id = 0,
                        dateReceived = System.currentTimeMillis() - 3600000, // 1 hour ago
                        content = "Hello world! This is a test message.",
                        sender = TestDeviceService.TEST_DEVICE_NAME,
                        chat = "local-user-test-device-uuid"  // Use ONLY conversation ID format
                    )

                    //insert the message
                    db.messageDao().addMessage(onlineTestMessage)

                    Log.d(
                        "GlobalApp",
                        "Inserted online test message with chat name: local-user-test-device-uuid"
                    )

                    //update convo with a test message
                    GlobalUserRepo.conversationRepository.updateWithMessage(
                        conversationId = onlineConversation.id,
                        message = onlineTestMessage
                    )

                    //create offline test user and conversation
                    val offlineUser = UserEntity(
                        uuid = "offline-test-device-uuid",
                        name = TestDeviceService.TEST_DEVICE_NAME_OFFLINE,
                        address = null // null address means offline
                    )

                    //make sure the offline test user exists in the database
                    GlobalUserRepo.userRepository.insertOrUpdateUser(
                        offlineUser.uuid,
                        offlineUser.name,
                        offlineUser.address
                    )

                    //create convo with the offline test device
                    val offlineConversation =
                        GlobalUserRepo.conversationRepository.getOrCreateConversation(
                            localUuid = localUuid,
                            remoteUser = offlineUser
                        )

                    //create offline test message - use ONLY the conversation ID format
                    val offlineTestMessage = Message(
                        id = 0,
                        dateReceived = System.currentTimeMillis() - 3600000, // 1 hour ago
                        content = "I'm currently offline. Messages won't be delivered.",
                        sender = TestDeviceService.TEST_DEVICE_NAME_OFFLINE,
                        chat = "local-user-offline-test-device-uuid"  // Use ONLY conversation ID format
                    )

                    //update test device statuses
                    DeviceStatusManager.updateDeviceStatus(TestDeviceService.TEST_DEVICE_IP, true)
                    DeviceStatusManager.updateDeviceStatus(TestDeviceService.TEST_DEVICE_IP_OFFLINE, false)

                    // Insert the message
                    db.messageDao().addMessage(offlineTestMessage)

                    Log.d(
                        "GlobalApp",
                        "Inserted offline test message with chat name: local-user-offline-test-device-uuid"
                    )

                    //update convo with the message
                    GlobalUserRepo.conversationRepository.updateWithMessage(
                        conversationId = offlineConversation.id,
                        message = offlineTestMessage
                    )
                    Log.d("GlobalApp", "Test messages inserted successfully")
                }else {
                    Log.d("GlobalApp", "Messages already exist, skipping insertion")
                }
            }catch (e: Exception ) {
                Log.e("GlobalApp", "Error inserting test conversation", e)
            }
        }
    }
    @SuppressLint("SimpleDateFormat")
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
                .connectTimeout(Duration.ofSeconds(30))
                // The maximum time to wait for data to be read from the server
                .readTimeout(Duration.ofSeconds(30))
                // The maximum time to wait for data to be written to the server
                .writeTimeout(Duration.ofSeconds(30))
                .build()
        }

        bind<MeshDatabase>() with singleton {
            Room.databaseBuilder(applicationContext,
                MeshDatabase::class.java,
                "mesh-database"
            )
                .addMigrations(object : Migration(3,4){
                    override fun migrate(database: SupportSQLiteDatabase){

                        //create convo table
                        database.execSQL(
                            """
                                CREATE TABLE IF NOT EXISTS conversations ( 
                                    id TEXT PRIMARY KEY NOT NULL, 
                                    user_uuid TEXT NOT NULL, 
                                    user_name TEXT NOT NULL, 
                                    user_address TEXT, 
                                    last_message TEXT, 
                                    last_message_time INTEGER NOT NULL, 
                                    unread_count INTEGER NOT NULL DEFAULT 0, 
                                    is_online INTEGER NOT NULL DEFAULT 0
                            )
                            """
                        )
                    }
                })
                .fallbackToDestructiveMigration() // handle migrations destructively
                .build()
        }

        bind<SharedPreferences>(tag = "settings") with singleton {
            applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }
        bind<UserRepository>() with singleton {
            UserRepository(instance<MeshDatabase>().userDao())
        }

        bind<ConversationRepository>() with singleton {
            ConversationRepository(instance<MeshDatabase>().conversationDao(), di)
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
                db = instance(),
                userRepository = instance()
            )
        }

        onReady {
            val logger: MNetLogger = instance()
            instance<AppServer>().start()
            logger(Log.DEBUG,"AppServer started successfully on Port: ${AppServer.DEFAULT_PORT}")
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