package com.njarrar.backthen.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
    /** Long side of the photos loaded for the video. Crisp on 9:16 fill reels with Ken Burns zoom. */
    const val PHOTO_SIDE = 2160

    private const val STALE_CACHE_MS = 60L * 60L * 1000L

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
                // Prune only stale reels (> 1 hour old) so an active share target isn't disrupted.
                val cutoff = System.currentTimeMillis() - STALE_CACHE_MS
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
                val baseName = fileName()
                var file = File(dir, "$baseName.mp4")
                var suffix = 1
                while (file.exists()) {
                    file = File(dir, "$baseName-$suffix.mp4")
                    suffix++
                }
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

    /** "Flashback 2026-11-03 19.40.12": a plain name that sorts by date where the file lands. */
    fun fileName(now: Date = Date()): String = "Flashback " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(now)

    private data class EncoderChoice(
        val codecName: String?,
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val includeColorSpace: Boolean = true,
    )

    private fun alignDown(value: Int, alignment: Int): Int {
        val a = maxOf(2, alignment)
        return maxOf(a, (value / a) * a)
    }

    /** Returns prioritized AVC encoder candidates (hardware first, then software fallback) matching capabilities. */
    private fun chooseEncoders(reqW: Int, reqH: Int): List<EncoderChoice> {
        val targetBitrate = ((reqW.toLong() * reqH.toLong() * 9_000_000L) / (1080L * 1920L))
            .toInt()
            .coerceIn(4_500_000, 9_000_000)
        val infos = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }.getOrNull().orEmpty()
        val avcEncoders = infos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        }.sortedBy { info ->
            val name = info.name.lowercase(Locale.ROOT)
            val sw = if (Build.VERSION.SDK_INT >= 29) info.isSoftwareOnly else (name.startsWith("omx.google.") || name.startsWith("c2.android."))
            if (sw) 1 else 0
        }
        val candidates = ArrayList<EncoderChoice>()
        for (info in avcEncoders) {
            val codecCaps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull() ?: continue
            val supportsSurface = codecCaps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            if (!supportsSurface) continue
            val videoCaps = codecCaps.videoCapabilities ?: continue
            val w = alignDown(reqW, videoCaps.widthAlignment)
            val h = alignDown(reqH, videoCaps.heightAlignment)
            val supported = runCatching { videoCaps.areSizeAndRateSupported(w, h, ReelPlan.FPS.toDouble()) }.getOrDefault(false) ||
                videoCaps.isSizeSupported(w, h)
            if (!supported) continue
            val clampedBitrate = targetBitrate.coerceIn(videoCaps.bitrateRange.lower, videoCaps.bitrateRange.upper)
            candidates += EncoderChoice(info.name, w, h, clampedBitrate, includeColorSpace = true)
            candidates += EncoderChoice(info.name, w, h, clampedBitrate, includeColorSpace = false)
        }
        if (candidates.isEmpty()) {
            val w = alignDown(reqW, 16)
            val h = alignDown(reqH, 16)
            candidates += EncoderChoice(null, w, h, targetBitrate, includeColorSpace = true)
            candidates += EncoderChoice(null, w, h, targetBitrate, includeColorSpace = false)
        }
        return candidates
    }

    private fun openEncoder(candidates: List<EncoderChoice>, file: File): Pair<EncoderChoice, Encoder> {
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                if (file.exists()) file.delete()
                return candidate to Encoder(
                    codecName = candidate.codecName,
                    width = candidate.width,
                    height = candidate.height,
                    bitrate = candidate.bitrate,
                    includeColorSpace = candidate.includeColorSpace,
                    file = file,
                )
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No compatible AVC encoder available")
    }

    private suspend fun write(renderer: ReelRenderer, file: File, load: suspend (Int) -> Bitmap?, progress: (Float) -> Unit) {
        val candidates = chooseEncoders(renderer.options.size.width, renderer.options.size.height)
        val (choice, createdEncoder) = openEncoder(candidates, file)
        val w = choice.width
        val h = choice.height
        val plan = renderer.plan
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        // Full-size photos, loaded as the reel reaches them and recycled once passed.
        val full = HashMap<Int, Bitmap?>()
        renderer.image = { full[it] }
        val encoder: Encoder = createdEncoder
        val ctx = coroutineContext
        val activeCheck: () -> Unit = { ctx.ensureActive() }
        try {
            val frames = plan.frameCount
            val evict = ArrayList<Int>(4)
            var lastReportedPct = -1
            for (f in 0 until frames) {
                coroutineContext.ensureActive()
                val t = f.toDouble() / ReelPlan.FPS
                val m = plan.moment(t)
                val idxA = (m.a.kind as? ReelPlan.Kind.Photo)?.index
                val idxB = (m.b?.kind as? ReelPlan.Kind.Photo)?.index
                if (idxA != null && idxA !in full) full[idxA] = load(idxA)
                if (idxB != null && idxB !in full) full[idxB] = load(idxB)
                evict.clear()
                for (k in full.keys) if (k != idxA && k != idxB) evict += k
                for (k in evict) full.remove(k)?.recycle()
                frame.eraseColor(android.graphics.Color.BLACK)
                renderer.draw(canvas, w.toFloat(), h.toFloat(), t)
                encoder.frame(frame, f * 1_000_000_000L / ReelPlan.FPS, activeCheck)
                val pct = (f + 1).toFloat() / frames
                val pctInt = (pct * 100f).toInt().coerceIn(0, 100)
                if (pctInt != lastReportedPct || f == frames - 1) {
                    lastReportedPct = pctInt
                    withContext(Dispatchers.Main.immediate) { progress(pct) }
                }
            }
            encoder.finish(activeCheck)
        } finally {
            encoder.release()
            renderer.image = { null }
            for (bmp in full.values) bmp?.recycle()
            full.clear()
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
    private class Encoder(
        codecName: String?,
        private val width: Int,
        private val height: Int,
        bitrate: Int,
        includeColorSpace: Boolean,
        file: File,
    ) {
        private var codec: MediaCodec? = null
        private var codecStarted = false
        private var muxer: MediaMuxer? = null
        private var input: Surface? = null
        private var track = -1
        private var muxing = false
        private var samplesWritten = 0
        private var lastPtsUs = -1L
        private val info = MediaCodec.BufferInfo()
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var texture = 0
        private val quad: ByteBuffer

        init {
            // x, y, u, v for two triangles covering the frame. The picture's top row sits at the top.
            val v = floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
            quad = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).apply { asFloatBuffer().put(v) }
            try {
                val enc = if (codecName != null) {
                    MediaCodec.createByCodecName(codecName)
                } else {
                    MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                }
                codec = enc
                muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, ReelPlan.FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    if (includeColorSpace) {
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    }
                }
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                input = enc.createInputSurface()
                enc.start()
                codecStarted = true
                setUpGl()
            } catch (t: Throwable) {
                release()
                throw t
            }
        }

        private fun setUpGl() {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(dpy != EGL14.EGL_NO_DISPLAY) { "EGL display" }
            display = dpy
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

            val vert = shader(GLES20.GL_VERTEX_SHADER, VERTEX)
            val frag = try {
                shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
            } catch (t: Throwable) {
                GLES20.glDeleteShader(vert)
                throw t
            }
            val prog = GLES20.glCreateProgram()
            try {
                check(prog != 0) { "GL program" }
                GLES20.glAttachShader(prog, vert)
                GLES20.glAttachShader(prog, frag)
                GLES20.glLinkProgram(prog)
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] == 0) {
                    val log = GLES20.glGetProgramInfoLog(prog)
                    GLES20.glDeleteProgram(prog)
                    error("GL link failed: $log")
                }
                program = prog
            } finally {
                GLES20.glDeleteShader(vert)
                GLES20.glDeleteShader(frag)
            }

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texture = ids[0]
            check(texture != 0) { "GL texture" }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            check(id != 0) { "GL shader" }
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(id)
                GLES20.glDeleteShader(id)
                error("GL shader compile failed: $log")
            }
            return id
        }

        private var uploaded = false

        fun frame(bitmap: Bitmap, timeNs: Long, checkActive: () -> Unit) {
            drain(false, checkActive)
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

        fun finish(checkActive: () -> Unit) {
            codec?.signalEndOfInputStream()
            drain(true, checkActive)
            check(muxing && samplesWritten > 0) { "encoder wrote no frames" }
            val mux = checkNotNull(muxer) { "muxer missing" }
            // Finalize the MP4 container explicitly outside runCatching so ENOSPC/moov errors fail cleanly.
            muxing = false
            mux.stop()
        }

        /** Moves finished video out of the encoder into the file. */
        private fun drain(end: Boolean, checkActive: () -> Unit) {
            val enc = codec ?: return
            val mux = muxer ?: return
            val timeoutUs = if (end) 10_000L else 0L
            var waits = 0
            while (true) {
                if (end) checkActive()
                val index = enc.dequeueOutputBuffer(info, timeoutUs)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!end) return
                        // Gives up after about ten seconds rather than hang.
                        if (++waits > 1000) error("encoder stopped")
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxing) { "format changed twice" }
                        track = mux.addTrack(enc.outputFormat)
                        mux.start()
                        muxing = true
                    }
                    index >= 0 -> {
                        val data = enc.getOutputBuffer(index)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing && data != null) {
                            if (info.presentationTimeUs <= lastPtsUs) {
                                info.presentationTimeUs = lastPtsUs + 1L
                            }
                            lastPtsUs = info.presentationTimeUs
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            mux.writeSampleData(track, data, info)
                            samplesWritten++
                        }
                        enc.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        fun release() {
            // Tear down GL objects and EGL surface/context BEFORE stopping/releasing MediaCodec and its input Surface.
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
                    runCatching {
                        EGL14.eglMakeCurrent(display, surface, surface, context)
                        if (program != 0) {
                            GLES20.glDeleteProgram(program)
                            program = 0
                        }
                        if (texture != 0) {
                            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                            texture = 0
                        }
                    }
                }
                runCatching { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) }
                if (surface != EGL14.EGL_NO_SURFACE) {
                    runCatching { EGL14.eglDestroySurface(display, surface) }
                    surface = EGL14.EGL_NO_SURFACE
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    runCatching { EGL14.eglDestroyContext(display, context) }
                    context = EGL14.EGL_NO_CONTEXT
                }
                runCatching { EGL14.eglReleaseThread() }
                runCatching { EGL14.eglTerminate(display) }
                display = EGL14.EGL_NO_DISPLAY
            }
            if (codecStarted) {
                runCatching { codec?.stop() }
                codecStarted = false
            }
            runCatching { codec?.release() }
            codec = null
            runCatching { input?.release() }
            input = null
            if (muxing) {
                runCatching { muxer?.stop() }
                muxing = false
            }
            runCatching { muxer?.release() }
            muxer = null
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
