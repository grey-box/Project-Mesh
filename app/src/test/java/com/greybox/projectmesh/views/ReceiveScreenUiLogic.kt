// File: app/src/test/java/com/greybox/projectmesh/views/ReceiveScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for ReceiveScreen.kt behavior WITHOUT touching ReceiveScreen.kt.
 *
 * Since the real file mixes Android framework (Context/Intent/MediaStore/SAF),
 * our JVM tests focus on the deterministic "decision logic" that can be validated now:
 *
 * 1) Which transfers should be auto-accepted when autoFinishEnabled=true
 * 2) Which trailing actions are visible given status
 * 3) When openFile should attempt to open (guard conditions)
 * 4) Download routing: content:// => SAF, else default path
 * 5) Key stability rule: key = hashCode("${host}-${id}-${requestReceivedTime}")
 *
 * Later in androidTest, you can validate real clicks, intents, and storage APIs.
 */
private object ReceiveScreenUiLogic {

    enum class Status { PENDING, COMPLETED, DECLINED, FAILED }

    data class IncomingTransferInfoLike(
        val fromHostAddress: String,
        val id: String,
        val requestReceivedTime: Long,
        val name: String? = null,
        val status: Status,
        val hasFile: Boolean,
    )

    enum class TrailingUi {
        NONE,
        PENDING_ACCEPT_DECLINE,
        COMPLETED_DELETE_DOWNLOAD,
        DECLINED_OR_FAILED_DELETE_ONLY
    }

    /**
     * Mirrors:
     * if (autoFinishEnabled) incomingTransfers.filter { status==PENDING }.forEach(onAccept)
     */
    fun transfersToAutoAccept(
        autoFinishEnabled: Boolean,
        transfers: List<IncomingTransferInfoLike>
    ): List<IncomingTransferInfoLike> {
        if (!autoFinishEnabled) return emptyList()
        return transfers.filter { it.status == Status.PENDING }
    }

    /**
     * Mirrors conditional rendering in ListItem supporting/trailing content.
     */
    fun trailingUiFor(status: Status): TrailingUi = when (status) {
        Status.PENDING -> TrailingUi.PENDING_ACCEPT_DECLINE
        Status.COMPLETED -> TrailingUi.COMPLETED_DELETE_DOWNLOAD
        Status.DECLINED, Status.FAILED -> TrailingUi.DECLINED_OR_FAILED_DELETE_ONLY
    }

    /**
     * Mirrors openFile guard:
     * if (file != null && status == COMPLETED) -> attempt open
     */
    fun shouldAttemptOpenFile(status: Status, hasFile: Boolean): Boolean {
        return hasFile && status == Status.COMPLETED
    }

    enum class DownloadRoute { SAF_CONTENT_URI, DEFAULT_PATH }

    /**
     * Mirrors:
     * if (uriOrPath.startsWith("content://")) saveFileToContentUri else saveFileToDefaultPath
     */
    fun downloadRoute(uriOrPath: String): DownloadRoute {
        return if (uriOrPath.startsWith("content://")) DownloadRoute.SAF_CONTENT_URI
        else DownloadRoute.DEFAULT_PATH
    }

    /**
     * Mirrors key expression:
     * key = {"${it.fromHost.hostAddress}-${it.id}-${it.requestReceivedTime}".hashCode()}
     */
    fun listKeyHash(fromHostAddress: String, id: String, requestReceivedTime: Long): Int {
        return "$fromHostAddress-$id-$requestReceivedTime".hashCode()
    }
}

class ReceiveScreenUiLogicTest {

    // ---------- Auto-accept logic ----------
    @Test
    fun transfersToAutoAccept_whenDisabled_returnsEmpty() {
        val transfers = listOf(
            ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "a", 1L, status = ReceiveScreenUiLogic.Status.PENDING, hasFile = false),
            ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "b", 2L, status = ReceiveScreenUiLogic.Status.COMPLETED, hasFile = true)
        )

        val result = ReceiveScreenUiLogic.transfersToAutoAccept(false, transfers)
        assertTrue(result.isEmpty())
    }

    @Test
    fun transfersToAutoAccept_whenEnabled_returnsOnlyPending_preservesOrder() {
        val t1 = ReceiveScreenUiLogic.IncomingTransferInfoLike("10.0.0.1", "1", 100L, status = ReceiveScreenUiLogic.Status.PENDING, hasFile = false)
        val t2 = ReceiveScreenUiLogic.IncomingTransferInfoLike("10.0.0.2", "2", 200L, status = ReceiveScreenUiLogic.Status.COMPLETED, hasFile = true)
        val t3 = ReceiveScreenUiLogic.IncomingTransferInfoLike("10.0.0.3", "3", 300L, status = ReceiveScreenUiLogic.Status.PENDING, hasFile = false)

        val result = ReceiveScreenUiLogic.transfersToAutoAccept(true, listOf(t1, t2, t3))

        assertEquals(listOf(t1, t3), result)
    }

    @Test
    fun transfersToAutoAccept_whenEnabled_andNoPending_returnsEmpty() {
        val transfers = listOf(
            ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "a", 1L, status = ReceiveScreenUiLogic.Status.COMPLETED, hasFile = true),
            ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "b", 2L, status = ReceiveScreenUiLogic.Status.DECLINED, hasFile = false)
        )

        val result = ReceiveScreenUiLogic.transfersToAutoAccept(true, transfers)
        assertTrue(result.isEmpty())
    }

    // ---------- Trailing UI decisions ----------
    @Test
    fun trailingUiFor_pending_showsAcceptDecline() {
        assertEquals(
            ReceiveScreenUiLogic.TrailingUi.PENDING_ACCEPT_DECLINE,
            ReceiveScreenUiLogic.trailingUiFor(ReceiveScreenUiLogic.Status.PENDING)
        )
    }

    @Test
    fun trailingUiFor_completed_showsDeleteDownload() {
        assertEquals(
            ReceiveScreenUiLogic.TrailingUi.COMPLETED_DELETE_DOWNLOAD,
            ReceiveScreenUiLogic.trailingUiFor(ReceiveScreenUiLogic.Status.COMPLETED)
        )
    }

    @Test
    fun trailingUiFor_declinedOrFailed_showsDeleteOnly() {
        assertEquals(
            ReceiveScreenUiLogic.TrailingUi.DECLINED_OR_FAILED_DELETE_ONLY,
            ReceiveScreenUiLogic.trailingUiFor(ReceiveScreenUiLogic.Status.DECLINED)
        )
        assertEquals(
            ReceiveScreenUiLogic.TrailingUi.DECLINED_OR_FAILED_DELETE_ONLY,
            ReceiveScreenUiLogic.trailingUiFor(ReceiveScreenUiLogic.Status.FAILED)
        )
    }

    // ---------- openFile guard ----------
    @Test
    fun shouldAttemptOpenFile_onlyWhenCompleted_andHasFile() {
        assertTrue(ReceiveScreenUiLogic.shouldAttemptOpenFile(ReceiveScreenUiLogic.Status.COMPLETED, hasFile = true))

        assertFalse(ReceiveScreenUiLogic.shouldAttemptOpenFile(ReceiveScreenUiLogic.Status.COMPLETED, hasFile = false))
        assertFalse(ReceiveScreenUiLogic.shouldAttemptOpenFile(ReceiveScreenUiLogic.Status.PENDING, hasFile = true))
        assertFalse(ReceiveScreenUiLogic.shouldAttemptOpenFile(ReceiveScreenUiLogic.Status.DECLINED, hasFile = true))
        assertFalse(ReceiveScreenUiLogic.shouldAttemptOpenFile(ReceiveScreenUiLogic.Status.FAILED, hasFile = true))
    }

    // ---------- Download routing ----------
    @Test
    fun downloadRoute_contentUri_goesToSAF() {
        assertEquals(
            ReceiveScreenUiLogic.DownloadRoute.SAF_CONTENT_URI,
            ReceiveScreenUiLogic.downloadRoute("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        )
    }

    @Test
    fun downloadRoute_filePath_goesToDefaultPath() {
        assertEquals(
            ReceiveScreenUiLogic.DownloadRoute.DEFAULT_PATH,
            ReceiveScreenUiLogic.downloadRoute("/storage/emulated/0/Download/Project Mesh")
        )
    }

    @Test
    fun downloadRoute_edgeCases_nonContentSchemes_goToDefaultPath() {
        assertEquals(ReceiveScreenUiLogic.DownloadRoute.DEFAULT_PATH, ReceiveScreenUiLogic.downloadRoute("file:///sdcard/Download"))
        assertEquals(ReceiveScreenUiLogic.DownloadRoute.DEFAULT_PATH, ReceiveScreenUiLogic.downloadRoute("http://example.com/x"))
        assertEquals(ReceiveScreenUiLogic.DownloadRoute.DEFAULT_PATH, ReceiveScreenUiLogic.downloadRoute(""))
        assertEquals(ReceiveScreenUiLogic.DownloadRoute.DEFAULT_PATH, ReceiveScreenUiLogic.downloadRoute("CONTENT://not-matching-case")) // case-sensitive
    }

    // ---------- Key hash stability ----------
    @Test
    fun listKeyHash_sameInputs_sameHash() {
        val a = ReceiveScreenUiLogic.listKeyHash("10.0.0.1", "abc", 123L)
        val b = ReceiveScreenUiLogic.listKeyHash("10.0.0.1", "abc", 123L)
        assertEquals(a, b)
    }

    @Test
    fun listKeyHash_changeAnyField_changesHash_mostOfTime() {
        val base = ReceiveScreenUiLogic.listKeyHash("10.0.0.1", "abc", 123L)

        val diffHost = ReceiveScreenUiLogic.listKeyHash("10.0.0.2", "abc", 123L)
        val diffId = ReceiveScreenUiLogic.listKeyHash("10.0.0.1", "abd", 123L)
        val diffTime = ReceiveScreenUiLogic.listKeyHash("10.0.0.1", "abc", 124L)

        // Hash collisions are theoretically possible, but extremely unlikely for these small strings.
        assertNotEquals(base, diffHost)
        assertNotEquals(base, diffId)
        assertNotEquals(base, diffTime)
    }

    // ---------- Combined scenario test ----------
    @Test
    fun scenario_pendingTransfers_autoAcceptTargetsMatch_andTrailingUiMatches() {
        val tPending1 = ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "1", 1L, status = ReceiveScreenUiLogic.Status.PENDING, hasFile = false)
        val tPending2 = ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "2", 2L, status = ReceiveScreenUiLogic.Status.PENDING, hasFile = false)
        val tDone = ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "3", 3L, status = ReceiveScreenUiLogic.Status.COMPLETED, hasFile = true)
        val tFail = ReceiveScreenUiLogic.IncomingTransferInfoLike("1.1.1.1", "4", 4L, status = ReceiveScreenUiLogic.Status.FAILED, hasFile = true)

        val transfers = listOf(tPending1, tDone, tPending2, tFail)

        val auto = ReceiveScreenUiLogic.transfersToAutoAccept(true, transfers)
        assertEquals(listOf(tPending1, tPending2), auto)

        assertEquals(ReceiveScreenUiLogic.TrailingUi.PENDING_ACCEPT_DECLINE, ReceiveScreenUiLogic.trailingUiFor(tPending1.status))
        assertEquals(ReceiveScreenUiLogic.TrailingUi.COMPLETED_DELETE_DOWNLOAD, ReceiveScreenUiLogic.trailingUiFor(tDone.status))
        assertEquals(ReceiveScreenUiLogic.TrailingUi.DECLINED_OR_FAILED_DELETE_ONLY, ReceiveScreenUiLogic.trailingUiFor(tFail.status))

        assertTrue(ReceiveScreenUiLogic.shouldAttemptOpenFile(tDone.status, tDone.hasFile))
        assertFalse(ReceiveScreenUiLogic.shouldAttemptOpenFile(tPending1.status, tPending1.hasFile))
    }
}