package com.njarrar.backthen.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.njarrar.backthen.core.ReelPlan
import com.njarrar.backthen.ui.ReelRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/** Writes a reel to an MP4 on the phone, frame by frame. Nothing leaves the phone. */
object ReelExport {
    /** Long side of the photos loaded for the video. Enough for a 1080 wide frame with the slow zoom. */
    const val PHOTO_SIDE = 1600

    /**
     * Makes the video in the app's cache and returns it. [progress] gets 0 to 1. Cancelling the
     * coroutine stops it and removes the half-made file. [load] gets a full-size photo by its place in the reel.
     */
    suspend fun make(
        context: Context,
        renderer: ReelRenderer,
        load: suspend (Int) -> Bitmap?,
        progress: (Float) -> Unit,
    ): File {
        // OpenGL works on the thread that set it up, so the whole video is made on one thread.
        val thread = Executors.newSingleThreadExecutor { Thread(it, "reel") }.asCoroutineDispatcher()
        try {
            return withContext(thread) {
                val dir = File(context.cacheDir, "reels").apply { mkdirs() }
                // One reel at a time on disk: old ones went out through the share sheet already.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "${fileName()}.mp4")
                try {
                    write(renderer, file, load, progress)
                    file
                } catch (e: Throwable) {
                    file.delete()
                    throw e
                }
            }
        } finally {
            thread.close()
        }
    }

    /** "Flashback 2026-11-03 19.40": a plain name that sorts by date where the file lands. */
    fun fileName(now: Date = Date()): String = "Flashback " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(now)

    private suspend fun write(renderer: ReelRenderer, file: File, load: suspend (Int) -> Bitmap?, progress: (Float) -> Unit) {
        val w = renderer.options.size.width
        val h = renderer.options.size.height
        val plan = renderer.plan
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        // Full-size photos, loaded as the reel reaches them and dropped once it has passed.
        val full = HashMap<Int, Bitmap?>()
        renderer.image = { synchronized(full) { full[it] } }
        val encoder = Encoder(w, h, file)
        try {
            val frames = plan.frameCount
            for (f in 0 until frames) {
                coroutineContext.ensureActive()
                val t = f.toDouble() / ReelPlan.FPS
                val m = plan.moment(t)
                val needed = listOfNotNull(m.a, m.b).mapNotNull { (it.kind as? ReelPlan.Kind.Photo)?.index }.toSet()
                for (i in needed) if (synchronized(full) { i !in full }) {
                    val bmp = load(i)
                    synchronized(full) { full[i] = bmp }
                }
                synchronized(full) { full.keys.filter { it !in needed }.forEach { full.remove(it) } }
                frame.eraseColor(android.graphics.Color.BLACK)
                renderer.draw(canvas, w.toFloat(), h.toFloat(), t)
                encoder.frame(frame, f * 1_000_000_000L / ReelPlan.FPS)
                progress((f + 1).toFloat() / frames)
            }
            encoder.finish()
        } finally {
            encoder.release()
            renderer.image = { null }
            frame.recycle()
        }
    }

    /** Copies a reel to Movies/Flashback, where the gallery shows it. Android 10 and up. */
    suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Flashback")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@runCatching false
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        }.getOrDefault(false)
    }

    /**
     * H.264 through the phone's video encoder. Frames go in as pictures drawn with OpenGL onto
     * the encoder's input surface, each with its own time, and come out into an MP4.
     */
    private class Encoder(private val width: Int, private val height: Int, file: File) {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val input: Surface
        private var track = -1
        private var muxing = false
        private val info = MediaCodec.BufferInfo()
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var texture = 0
        private val quad: ByteBuffer

        init {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 9_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, ReelPlan.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = codec.createInputSurface()
            codec.start()
            setUpGl()
            // x, y, u, v for two triangles covering the frame. The picture's top row sits at the top.
            val v = floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
            quad = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(v) }
        }

        private fun setUpGl() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL init" }
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "EGL config" }
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "EGL context" }
            surface = EGL14.eglCreateWindowSurface(display, configs[0], input, intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "EGL surface" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "EGL current" }

            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
            GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
            GLES20.glLinkProgram(program)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texture = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }

        private var uploaded = false

        fun frame(bitmap: Bitmap, timeNs: Long) {
            drain(false)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            if (uploaded) GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap) else GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            uploaded = true
            val pos = GLES20.glGetAttribLocation(program, "pos")
            val uv = GLES20.glGetAttribLocation(program, "uv")
            quad.position(0)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(pos)
            quad.position(8)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "tex"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGLExt.eglPresentationTimeANDROID(display, surface, timeNs)
            check(EGL14.eglSwapBuffers(display, surface)) { "EGL swap" }
        }

        fun finish() {
            codec.signalEndOfInputStream()
            drain(true)
        }

        /** Moves finished video out of the encoder into the file. */
        private fun drain(end: Boolean) {
            var waits = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end) return
                        // Gives up after about ten seconds rather than hang.
                        if (++waits > 1000) error("encoder stopped")
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxing) { "format changed twice" }
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    index >= 0 -> {
                        val data = codec.getOutputBuffer(index)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing && data != null) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        fun release() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            }
            input.release()
            runCatching { if (muxing) muxer.stop() }
            runCatching { muxer.release() }
        }

        companion object {
            private const val EGL_RECORDABLE_ANDROID = 0x3142
            private const val VERTEX = """
                attribute vec2 pos;
                attribute vec2 uv;
                varying vec2 v;
                void main() { v = uv; gl_Position = vec4(pos, 0.0, 1.0); }
            """
            private const val FRAGMENT = """
                precision mediump float;
                varying vec2 v;
                uniform sampler2D tex;
                void main() { gl_FragColor = texture2D(tex, v); }
            """
        }
    }
}
