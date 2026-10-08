package com.liufy.thermaldisplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Fail-safe player used only if the device cannot decode the native animated WebP. */
class FallbackFramePlayer(
    private val context: Context,
    private val view: ZoomableMediaViewport
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(context.cacheDir, "v19_model_frames")
    @Volatile private var ready=false
    @Volatile private var playing=false
    @Volatile private var pending=false
    private var frame=0
    private var oldBitmap: Bitmap?=null
    private val frameDelay=1000L/24L

    fun prepare(autoStart: Boolean) {
        executor.execute {
            ensureExtracted()
            ready=true
            main.post {
                showFrame(0, resetTransform = true) {
                    if(autoStart) start()
                }
            }
        }
    }

    fun start() {
        playing=true
        schedule()
    }

    fun stop() { playing=false }

    fun reset() {
        playing=false
        frame=0
        if(ready) showFrame(0, resetTransform = true, after = null)
    }

    fun release() {
        playing=false
        executor.shutdownNow()
        oldBitmap?.recycle()
        oldBitmap=null
    }

    private fun schedule() {
        if(!playing || !ready || pending) return
        pending=true
        val current=frame
        val start=System.currentTimeMillis()
        executor.execute {
            val file=File(dir,"frame_%03d.webp".format(current))
            val bmp=BitmapFactory.decodeFile(file.absolutePath)
            val elapsed=System.currentTimeMillis()-start
            main.post {
                if(bmp!=null) {
                    val prev=oldBitmap
                    oldBitmap=bmp
                    view.setImageDrawablePreserveTransform(BitmapDrawable(context.resources,bmp))
                    prev?.recycle()
                }
                frame=(frame+1)%120
                pending=false
                if(playing) main.postDelayed({ schedule() }, (frameDelay-elapsed).coerceAtLeast(1L))
            }
        }
    }

    private fun showFrame(index:Int, resetTransform:Boolean, after:(()->Unit)?) {
        executor.execute {
            val file=File(dir,"frame_%03d.webp".format(index))
            val bmp=BitmapFactory.decodeFile(file.absolutePath)
            main.post {
                if(bmp!=null) {
                    val prev=oldBitmap
                    oldBitmap=bmp
                    view.setImageDrawablePreserveTransform(BitmapDrawable(context.resources,bmp))
                    if(resetTransform) view.post { view.resetTransform() }
                    prev?.recycle()
                }
                after?.invoke()
            }
        }
    }

    private fun ensureExtracted() {
        if(File(dir,"frame_119.webp").exists()) return
        dir.mkdirs()
        context.assets.open("media/model_frames.zip").use { input ->
            ZipInputStream(input).use { zip ->
                var entry=zip.nextEntry
                val buf=ByteArray(64*1024)
                while(entry!=null) {
                    if(!entry.isDirectory && entry.name.endsWith(".webp")) {
                        val out=File(dir,File(entry.name).name)
                        FileOutputStream(out).use { fos ->
                            while(true) {
                                val n=zip.read(buf)
                                if(n<=0) break
                                fos.write(buf,0,n)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry=zip.nextEntry
                }
            }
        }
    }
}
