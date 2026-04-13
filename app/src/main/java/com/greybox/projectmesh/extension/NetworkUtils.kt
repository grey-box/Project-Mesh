package com.greybox.projectmesh.extension

import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import org.kodein.di.DI
import org.kodein.di.instance

/**
 * Retrieves the local IP address of the [AndroidVirtualNode] from a Kodein [DI] container.
 *
 * @param di The [DI] instance used to obtain the [AndroidVirtualNode].
 * @return The host IP address of the node as a [String].
 */
fun getLocalIpFromDI(di: DI): String {
    // Retrieve the AndroidVirtualNode from DI and return its IP address
    val node: AndroidVirtualNode by di.instance()
    return node.address.hostAddress
}
