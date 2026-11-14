package com.greybox.projectmesh.data

import com.greybox.projectmesh.server.AppServer

enum class FileSaveResult {
    SUCCESS,
    FILE_UNAVAILABLE,
    FOLDER_CREATE_FAILED,
    SAVE_FAILED
}

interface ReceivedFileSaver {
    suspend fun save(
        transfer: AppServer.IncomingTransferInfo,
        destination: String
    ): FileSaveResult
}