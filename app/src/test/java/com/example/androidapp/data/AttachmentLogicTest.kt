package com.example.androidapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentLogicTest {

    private fun pending(
        name: String = "report.pdf",
        size: Long = 1000L,
        mime: String = "application/pdf"
    ) = PendingAttachment("content://x/1", name, size, mime)

    @Test
    fun allowedTypes() {
        assertTrue(isAllowedAttachmentType("application/pdf"))
        assertTrue(isAllowedAttachmentType("image/png"))
        assertTrue(isAllowedAttachmentType("image/jpeg"))
        assertTrue(isAllowedAttachmentType("text/plain"))
        assertTrue(isAllowedAttachmentType("application/msword"))
        assertTrue(
            isAllowedAttachmentType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertFalse(isAllowedAttachmentType("application/x-msdownload"))
        assertFalse(isAllowedAttachmentType("application/octet-stream"))
        assertFalse(isAllowedAttachmentType(null))
    }

    @Test
    fun attachmentErrorChecksSizeAndType() {
        assertNull(attachmentError(pending()))
        assertNull(attachmentError(pending(size = MAX_ATTACHMENT_BYTES)))
        assertEquals(
            "Files must be 10 MB or smaller.",
            attachmentError(pending(size = MAX_ATTACHMENT_BYTES + 1))
        )
        assertTrue(attachmentError(pending(mime = "application/zip"))!!.contains("file type"))
    }

    @Test
    fun canSendNeedsTextOrAttachment() {
        assertFalse(canSend("", false))
        assertFalse(canSend("   ", false))
        assertTrue(canSend("hi", false))
        assertTrue(canSend("", true))          // attachment only
        assertTrue(canSend("see file", true))
        assertFalse(canSend("a".repeat(MAX_MESSAGE_LENGTH + 1), true))
    }

    @Test
    fun fileNamesAreSafeForStorage() {
        assertEquals("my_file.pdf", sanitizeFileName("my file.pdf"))
        assertEquals("a_b.pdf", sanitizeFileName("a/b.pdf"))
        assertEquals("file", sanitizeFileName("///"))
        assertEquals("file", sanitizeFileName(""))
        assertTrue(sanitizeFileName("x".repeat(300)).length <= 80)
        assertFalse(sanitizeFileName("../../etc/passwd").contains("/"))
    }

    @Test
    fun fileSizeFormatting() {
        assertEquals("500 B", formatFileSize(500))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("5.0 MB", formatFileSize(5L * 1024 * 1024))
    }

    @Test
    fun previewShowsFileNameWhenThereIsNoText() {
        val file = ChatAttachment("plan.pdf", "message-files/c/1_plan.pdf", 10L, "application/pdf")
        assertEquals("📎 plan.pdf", messagePreview("", file))
        assertEquals("📎 plan.pdf", messagePreview("   ", file))
        assertEquals("see attached", messagePreview("see attached", file))
        assertEquals("hello", messagePreview("hello", null))
    }
}