# User Profiles in Project Mesh

## Overview

Project Mesh implements a user profile system that allows devices to identify themselves on the mesh network. User profiles consist of a unique identifier (UUID), a display name, and network address information. This system enables personalized messaging and device identification across the mesh network.

## Key Components

### User Entity

The core of the user profile system is the UserEntity class, which stores all user data:

```kotlin
// In UserEntity.kt
@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val address: String? = null, // IP address, nullable
    val lastSeen: Long? = null
)
```

### User Repository

The UserRepository manages all database operations related to user profiles:

```kotlin
// In UserRepository.kt
class UserRepository(private val userDao: UserDao) {

    suspend fun insertOrUpdateUser(uuid: String, name: String, address: String?) {
        val existing = userDao.getUserByUuid(uuid)
        if (existing == null) {
            // Insert new user with address
            userDao.insertUser(
                UserEntity(
                    uuid = uuid,
                    name = name,
                    address = address
                )
            )
        } else {
            // Update existing user, copying over address
            userDao.updateUser(
                existing.copy(
                    name = name,
                    address = address
                )
            )
        }
    }

    // Other repository methods...
}
```

### UserData Access Object (DAO)

The UserDao interface defines database operations:

```kotlin
// In UserDao.kt
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE uuid = :uuid LIMIT 1")
    suspend fun getUserByUuid(uuid: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE uuid = :uuid)")
    suspend fun hasWithID(uuid: String): Boolean

    @Query("SELECT * FROM users WHERE address = :ip LIMIT 1")
    suspend fun getUserByIp(ip: String): UserEntity?

    @Query("SELECT * FROM users WHERE address IS NOT NULL")
    suspend fun getAllConnectedUsers(): List<UserEntity>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}
```

## User Profile Lifecycle

### First-time Setup

<img src="images/User_Onboarding.png" width="300" style="border: 2px solid black;" alt="User_Onboarding">

When a user first launches the app, they go through an onboarding process to set up their profile:

```kotlin
// In OnboardingViewModel.kt
fun handleFirstTimeSetup(onComplete: () -> Unit) {
    viewModelScope.launch {
        // Generate or retrieve the user's UUID
        var uuid = prefs.getString("UUID", null)
        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("UUID", uuid).apply()
        }
        // Insert or update the user with the local IP
        userRepository.insertOrUpdateUser(
            uuid = uuid,
            name = _uiState.value.username,
            address = localIp
        )
        prefs.edit().putString("device_name", _uiState.value.username).apply()
        prefs.edit().putBoolean("hasRunBefore", true).apply()
        onComplete()
    }
}
```

### User Information Exchange

When devices connect, they exchange user information:
**Before Name Exchange**:

<img src="images/NetworkScreenBeforeUpdate.png" width="300" style="border: 2px solid black;" alt="NetworkScreenBeforeUpdate">

**After Name Exchange**:

<img src="images/NetworkScreenPostUpdateBob.png" width="300" style="border: 2px solid black;" alt="NetworkScreenPostUpdate">

```kotlin
// In AppServer.kt - requesting user info
fun requestRemoteUserInfo(remoteAddr: InetAddress, port: Int = DEFAULT_PORT) {
    // Special handling for test devices...

    scope.launch {
        try {
            val url = "http://${remoteAddr.hostAddress}:$port/myinfo"
            val request = Request.Builder().url(url).build()
            Log.d("AppServer", "Requesting remote user info from $url")

            val response = httpClient.newCall(request).execute()
            val userJson = response.body?.string()

            if (!userJson.isNullOrEmpty()) {
                // Decode JSON
                val remoteUser = json.decodeFromString(UserEntity.serializer(), userJson)

                // Insert or update in DB
                userRepository.insertOrUpdateUser(
                    remoteUserWithIp.uuid,
                    remoteUserWithIp.name,
                    remoteUserWithIp.address
                )

                // Update user status...
            }
        } catch (e: Exception) {
            Log.e("AppServer", "Failed to fetch /myinfo from ${remoteAddr.hostAddress}", e)
        }
    }
}

// In AppServer.kt - providing user info
private fun handleMyInfoRequest(): Response {
    val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
    val localUuid = sharedPrefs.getString("UUID", null) ?: return newFixedLengthResponse(
        Response.Status.INTERNAL_ERROR, "application/json", """{"error":"No local UUID found"}"""
    )

    val localUser = runBlocking { userRepository.getUser(localUuid) } ?: return newFixedLengthResponse(
        Response.Status.INTERNAL_ERROR, "application/json", """{"error":"No local user record in DB"}"""
    )

    // Add the local IP to the address field
    val localUserWithAddr = localUser.copy(
        address = localVirtualAddr.hostAddress
    )

    val userJson = json.encodeToString(UserEntity.serializer(), localUserWithAddr)
    return newFixedLengthResponse(Response.Status.OK, "application/json", userJson)
}
```

### Profile Updates

Users can update their profile information in the Settings screen:
_Found in Settings Under Network > Device Name_:

<img src="images/UserNameSettings.png" width="300" style="border: 2px solid black;" alt="UserNameSettings">

_User name can be Updated_ :

<img src="images/EditingUserName.png" width="300" style="border: 2px solid black;" alt="EditingUserName">

```kotlin
// In SettingsScreen.kt
onDeviceNameChange = { newDeviceName ->
    Log.d("BottomNavApp", "Device name changed to: $newDeviceName")

    // Retrieve the local UUID from SharedPreferences
    val localUuid = settingsPrefs.getString("UUID", null)
    if (localUuid != null) {
        // Update the local user info and broadcast in an IO coroutine
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Update the local user in the database
            userRepository.insertOrUpdateUser(
                uuid = localUuid,
                name = newDeviceName,
                address = appServer.localVirtualAddr.hostAddress
            )

            // 2. Broadcast updated name to connected users
            val connectedUsers = userRepository.getAllConnectedUsers()
            connectedUsers.forEach { user ->
                user.address?.let { ip ->
                    try {
                        val remoteAddr = InetAddress.getByName(ip)
                        appServer.pushUserInfoTo(remoteAddr)
                    } catch (e: Exception) {
                        Log.e("BottomNavApp", "Error processing IP address: $ip", e)
                    }
                }
            }
        }
    }
}
```

### Online Status Tracking

The application tracks which users are online using the DeviceStatusManager:

```kotlin
// In DeviceStatusManager.kt
object DeviceStatusManager {
    private val _deviceStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val deviceStatusMap: StateFlow<Map<String, Boolean>> = _deviceStatusMap.asStateFlow()

    // Updates a device's online status
    fun updateDeviceStatus(ipAddress: String, isOnline: Boolean, verified: Boolean = false) {
        // Special handling for test devices...

        // For normal devices
        if (verified) {
            _deviceStatusMap.update { current ->
                val mutable = current.toMutableMap()
                mutable[ipAddress] = isOnline
                mutable
            }
            lastCheckedTimes[ipAddress] = System.currentTimeMillis()
        } else {
            // Handle unverified status updates...
        }
    }

    // Other status management methods...
}
```

## Integration with UI

User profiles are displayed in various parts of the UI:

### Netowork List

```kotlin
// In WifiListItem.kt
@Composable
fun WifiListItem(
    wifiAddress: Int,
    wifiEntry: VirtualNode.LastOriginatorMessage,
    onClick: ((nodeAddress: String) -> Unit)? = null,
) {
    // Other UI elements...

    // obtain the device name according to the ip address
    val user = runBlocking {
        GlobalApp.GlobalUserRepo.userRepository.getUserByIp(wifiAddressDotNotation)
    }
    val device = user?.name ?: "Unknown"
    if(device != null) {
        Text(text= device, fontWeight = FontWeight.Bold)
    }
    else {
        Text(text = "Loading...", fontWeight = FontWeight.Bold)
    }

    // Other UI elements...
}
```

### ChatScreen

```kotlin
// In ChatScreen.kt
@Composable
fun UserStatusBar(
    userName: String,
    isOnline: Boolean,
    userAddress: String
) {
    Card(
        // Card styling...
    ) {
        Row(
            // Row styling...
        ) {
            // User avatar/icon
            Box(
                // Avatar styling...
            ) {
                Text(
                    text = userName.first().toString(),
                    // Text styling...
                )
            }

            // User name and status
            Column {
                Text(
                    text = userName,
                    // Text styling...
                )

                Row(
                    // Row styling...
                ) {
                    // Status indicator dot...

                    // Status text
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        // Text styling...
                    )

                    // IP address
                    Text(
                        text = userAddress,
                        // Text styling...
                    )
                }
            }
        }
    }
}
```

### Conversations

User profiles are linked to conversations for messaging:

```kotlin
// In ConversationRepository.kt
suspend fun getOrCreateConversation(localUuid: String, remoteUser: UserEntity): Conversation {
    //create a unique conversation ID using both UUIDs in order to ensure consistency
    val conversationId = ConversationUtils.createConversationId(localUuid, remoteUser.uuid)

    //try to get an existing conversation
    var conversation = conversationDao.getConversationById(conversationId)

    //if no conversation exists, create a new one
    if(conversation == null) {
        conversation = Conversation(
            id = conversationId,
            userUuid = remoteUser.uuid,
            userName = remoteUser.name,
            userAddress = remoteUser.address,
            lastMessage = null,
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isOnline = remoteUser.address != null
        )
        conversationDao.insertConversation(conversation)
    }

    return conversation
}
```

## Technical Implementation Notes

1. **UUID Generation**: UUIDs are generated using Java's UUID.randomUUID() and stored in SharedPreferences.
2. **Persistent Storage**: User profiles are stored in a Room database, allowing them to persist across app restarts.
3. **Network Communication**: User profiles are serialized to JSON for transmission across the network.
4. **Online Status**: A device's online status is determined by regular connectivity checks and timeouts.
5. **Address Management**: IP addresses are managed dynamically based on network connectivity.

## Best Practices

When working with user profiles:

- **Always check for null**: IP addresses and user objects might be null, especially during initial setup.
- **Use background threads**: Database and network operations should run on IO dispatchers, not the main thread.
- **Handle device name changes**: When a user changes their device name, broadcast the change to all connected devices.
- **Respect privacy**: Be mindful of what user information is stored and shared across the network.
- **Handle connection loss**: Implement fallbacks for scenarios where user profile information cannot be retrieved.

## Troubleshooting

### Common Issues

1. **Missing User Profile**: If a device appears but has no name, it may indicate a failure in the user info exchange.
2. **Incorrect Online Status**: If a device shows incorrect online status, try refreshing the network view or restarting the app.
3. **Duplicate Users**: If multiple entries appear for the same user, there may be an issue with UUID generation or storage.

## Next Steps

1. **Add profile pictures**: Consider adding the ability for users to set profile pictures.
2. **Enhance privacy options**: Allow users to control what information is shared.
3. **Add user verification**: Implement a mechanism to verify user identities on the network.
