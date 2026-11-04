package com.moustayt.cloudvideo.generator

import com.moustayt.cloudvideo.generator.data.AppJson
import com.moustayt.cloudvideo.generator.data.DEMO_VIDEO
import com.moustayt.cloudvideo.generator.data.DemoService
import com.moustayt.cloudvideo.generator.data.GenRequest
import com.moustayt.cloudvideo.generator.data.JobDto
import com.moustayt.cloudvideo.generator.data.Kind
import com.moustayt.cloudvideo.generator.data.LibraryItem
import com.moustayt.cloudvideo.generator.data.ServerStatus
import com.moustayt.cloudvideo.generator.data.Status
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTest {
    @Test
    fun kindFlags() {
        assertTrue(Kind.TEXT_TO_VIDEO.isVideo)
        assertTrue(Kind.IMAGE_TO_VIDEO.isVideo)
        assertFalse(Kind.TEXT_TO_IMAGE.isVideo)
        assertTrue(Kind.IMAGE_EDIT.needsImage)
        assertTrue(Kind.IMAGE_TO_VIDEO.needsImage)
        assertFalse(Kind.TEXT_TO_VIDEO.needsImage)
    }

    @Test
    fun requestSerializesTheFieldsTheBackendExpects() {
        val json = AppJson.encodeToString(GenRequest(Kind.TEXT_TO_IMAGE, "a cat", "anime", "16:9", 5))
        assertTrue(json.contains("\"kind\":\"TEXT_TO_IMAGE\""))
        assertTrue(json.contains("\"prompt\":\"a cat\""))
        assertTrue(json.contains("\"aspect\":\"16:9\""))
        assertTrue(json.contains("\"durationSec\":5"))
    }

    @Test
    fun backendResponsesDecodeAndIgnoreUnknownKeys() {
        val job = AppJson.decodeFromString<JobDto>("""{"id":"fal.abc","status":"DONE","progress":1,"resultUrl":"https://x/y.png","extra":1}""")
        assertEquals(Status.DONE, job.status)
        assertEquals("https://x/y.png", job.resultUrl)

        val st = AppJson.decodeFromString<ServerStatus>("""{"ok":true,"owner":true,"quota":false,"providers":["fal"]}""")
        assertTrue(st.owner)
        assertEquals(listOf("fal"), st.providers)
    }

    @Test
    fun libraryItemRoundTrips() {
        val item = LibraryItem("id1", Kind.TEXT_TO_VIDEO, "p", "anime", "9:16", 1L, Status.RUNNING, 0.5f)
        assertEquals(item, AppJson.decodeFromString<LibraryItem>(AppJson.encodeToString(item)))
    }

    @Test
    fun demoServiceProgressesAndFinishes() = runTest {
        val demo = DemoService()
        val id = demo.submit(GenRequest(Kind.TEXT_TO_IMAGE, "x", "anime", "1:1"))
        val early = demo.poll(id)
        assertTrue(early.status == Status.QUEUED || early.status == Status.RUNNING)
        assertNull(early.resultUrl)

        val oldVideo = demo.poll("demo.TEXT_TO_VIDEO.${System.currentTimeMillis() - 10_000}.abc")
        assertEquals(Status.DONE, oldVideo.status)
        assertEquals(DEMO_VIDEO, oldVideo.resultUrl)

        val oldImage = demo.poll("demo.TEXT_TO_IMAGE.${System.currentTimeMillis() - 10_000}.abc")
        assertEquals(Status.DONE, oldImage.status)
        assertTrue(oldImage.resultUrl!!.contains("picsum.photos/seed/abc"))
    }
}
