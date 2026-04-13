package com.greybox.projectmesh.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greybox.projectmesh.GlobalApp
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import kotlinx.coroutines.runBlocking
import com.greybox.projectmesh.user.UserRepository

/**
 * Displays a single Wi-Fi node in a list with device information and mesh network status.
 *
 * This composable shows the device icon, name (from IP address), IP in dot notation,
 * and mesh network details including ping time and hop count.
 *
 * @param wifiAddress The integer IP address of the Wi-Fi node.
 * @param wifiEntry The [VirtualNode.LastOriginatorMessage] containing the node's mesh message data.
 * @param onClick Optional lambda invoked when the list item is clicked, providing the node's IP in dot notation.
 */
@Composable
fun WifiListItem(
    wifiAddress: Int,
    wifiEntry: VirtualNode.LastOriginatorMessage,
    onClick: ((nodeAddress: String) -> Unit)? = null,
){
    val wifiAddressDotNotation = wifiAddress.addressToDotNotation()
    ListItem(
        modifier = Modifier.fillMaxWidth().let{
            if(onClick != null){
                it.clickable(onClick = {
                    onClick(wifiAddressDotNotation)
                })
            }
            else{
                it
            }
        },
        leadingContent = {
            // The image icon on the left side
            Icon(
                // replace this image with a custom icon or image
                imageVector = Icons.Default.Image,
                contentDescription = "Profile Picture",
                modifier = Modifier.size(28.dp)
            )
        },
        headlineContent = {
            // obtain the device name according to the ip address
            val user = runBlocking {
                GlobalApp.GlobalUserRepo.userRepository.getUserByIp(wifiAddressDotNotation)
            }
            val device = user?.name ?: "Unknown"
            if(device != null){
                Text(text= device, fontWeight = FontWeight.Bold)
            }
            else{
                Text(text = "Loading...", fontWeight = FontWeight.Bold)
            }
        },
        supportingContent = {
            Text(text = wifiAddressDotNotation)
        },
        trailingContent = {
            // The mesh status with signal bars and text
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = "Mesh Signal Strength",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column{
                    Text(text = "Mesh status")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ping: ${wifiEntry.originatorMessage.pingTimeSum}ms "
                            + "Hops: ${wifiEntry.hopCount} ")
                }
            }
        }
    )
    HorizontalDivider()
}
