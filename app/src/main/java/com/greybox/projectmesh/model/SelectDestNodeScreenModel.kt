package com.greybox.projectmesh.model

import com.ustadmobile.meshrabiya.vnet.VirtualNode

data class SelectDestNodeScreenModel(
    val allNodes: Map<Int, VirtualNode.LastOriginatorMessage> = emptyMap(),
    val uri: String = "",
    val contactingInProgressDevice: String? = null,
)