package com.greybox.projectmesh.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.greybox.projectmesh.GlobalApp
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import kotlinx.coroutines.runBlocking
import com.greybox.projectmesh.ui.theme.MeshTheme
@Composable
// Display a single connected wifi station
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
                modifier = Modifier.size(MeshTheme.size.iconMd)
            )
        },
        headlineContent = {
            // obtain the device name according to the ip address
            val user = runBlocking {
                GlobalApp.GlobalUserRepo.userRepository.getUserByIp(wifiAddressDotNotation)
            }
            // getUserByIp already returns null for an unknown node, so the fallback is the
            // only "not found" state there is. The previous `if (device != null)` could never
            // be false once the elvis had run, so the "Loading..." branch was unreachable.
            Text(text = user?.name ?: "Unknown", fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.size(MeshTheme.size.iconMd)
                )
                Spacer(modifier = Modifier.width(MeshTheme.spacing.xs))
                // The gap between these two lines used to be a Spacer with a *width*, which
                // contributes nothing inside a Column, so the two labels sat flush against
                // each other. Expressing it as the Column's own vertical arrangement makes
                // the spacing real and removes the stray Spacer.
                Column(
                    verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.xs)
                ){
                    Text(text = "Mesh status")
                    Text("Ping: ${wifiEntry.originatorMessage.pingTimeSum}ms "
                            + "Hops: ${wifiEntry.hopCount} ")
                }
            }
        }
    )
    HorizontalDivider()
}
