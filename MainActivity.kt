package com.liufy.thermaldisplay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var dashboard: DashboardLayout
    private val main = Handler(Looper.getMainLooper())

    private var animatedModel: AnimatedImageDrawable? = null
    private var fallbackPlayer: FallbackFramePlayer? = null
    private var modelPlaying = true

    private val thermals = arrayOfNulls<BitmapDrawable>(4)
    private var thermalIndex = 0
    private var manualHoldUntil = 0L
    private val thermalTick = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() >= manualHoldUntil) showThermal((thermalIndex + 1) % 4, manual = false)
            main.postDelayed(this, 1200L)
        }
    }

    private var tapCount=0
    private var lastTap=0L
    private var diagnosticOverlay: TextView?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        dashboard=DashboardLayout(this)
        setContentView(dashboard)

        dashboard.resetButton.setOnClickListener { resetModel() }
        dashboard.playButton.setOnClickListener { toggleModel() }
        dashboard.thermalButtons.forEachIndexed { index, button ->
            button.setOnClickListener { showThermal(index, manual = true) }
        }
        dashboard.onHeaderTap={ handleHeaderTap() }

        loadThermals()
        loadModel(autoStart = true)
        main.postDelayed(thermalTick,1200L)
    }

    private fun loadThermals() {
        for(i in 0..3) {
            assets.open("media/thermal_${i+1}.png").use { input ->
                val bitmap=BitmapFactory.decodeStream(input)
                thermals[i]=BitmapDrawable(resources,bitmap)
            }
        }
        showThermal(0, manual=false)
    }

    private fun showThermal(index:Int, manual:Boolean) {
        thermalIndex=index.coerceIn(0,3)
        thermals[thermalIndex]?.let { dashboard.thermalView.setImageDrawablePreserveTransform(it) }
        dashboard.updateThermalActive(thermalIndex)
        if(manual) manualHoldUntil=System.currentTimeMillis()+3000L
    }

    private fun loadModel(autoStart:Boolean) {
        animatedModel?.stop()
        animatedModel=null
        fallbackPlayer?.release()
        fallbackPlayer=null

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source=ImageDecoder.createSource(assets,"media/model_rotation.webp")
                val drawable=ImageDecoder.decodeDrawable(source)
                if(drawable is AnimatedImageDrawable) {
                    drawable.repeatCount=AnimatedImageDrawable.REPEAT_INFINITE
                    dashboard.modelView.setImageDrawable(drawable)
                    dashboard.modelView.post { dashboard.modelView.resetTransform() }
                    animatedModel=drawable
                    modelPlaying=autoStart
                    if(autoStart) drawable.start()
                    dashboard.playButton.text=if(modelPlaying) "暂停" else "播放"
                    return
                }
            } catch (_: Throwable) {
                // Fall through to frame ZIP player.
            }
        }

        val fallback=FallbackFramePlayer(this,dashboard.modelView)
        fallbackPlayer=fallback
        modelPlaying=autoStart
        dashboard.playButton.text=if(modelPlaying) "暂停" else "播放"
        fallback.prepare(autoStart)
    }

    private fun toggleModel() {
        modelPlaying=!modelPlaying
        if(modelPlaying) {
            animatedModel?.start() ?: fallbackPlayer?.start()
            dashboard.playButton.text="暂停"
        } else {
            animatedModel?.stop() ?: fallbackPlayer?.stop()
            dashboard.playButton.text="播放"
        }
    }

    private fun resetModel() {
        dashboard.modelView.resetTransform()
        modelPlaying=false
        dashboard.playButton.text="播放"
        if(animatedModel!=null) {
            // Re-decoding guarantees a true first-frame reset for AnimatedImageDrawable.
            loadModel(autoStart=false)
        } else {
            fallbackPlayer?.reset()
        }
    }

    private fun handleHeaderTap() {
        val now=System.currentTimeMillis()
        if(now-lastTap>2200L) tapCount=0
        lastTap=now
        tapCount++
        if(tapCount>=5) {
            tapCount=0
            showDiagnostics()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showDiagnostics() {
        diagnosticOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val metrics=resources.displayMetrics
        val real=DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(real)
        val app=DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(app)
        val fontScale=resources.configuration.fontScale
        val webPkg=if(Build.VERSION.SDK_INT>=26) WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "Unknown" else "API<26"
        val insetsText=readInsets()
        val pm=packageManager
        val multiBasic=pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)
        val multiDistinct=pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        val multiJazz=pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND)
        val modelTouch="max=${dashboard.modelView.getMaxPointersSeen()} last=${dashboard.modelView.getLastPointerCount()} scale=${"%.2f".format(Locale.US,dashboard.modelView.getUserScale())} src=${dashboard.modelView.getLastInputSource()}"
        val thermalTouch="max=${dashboard.thermalView.getMaxPointersSeen()} last=${dashboard.thermalView.getLastPointerCount()} scale=${"%.2f".format(Locale.US,dashboard.thermalView.getUserScale())} src=${dashboard.thermalView.getLastInputSource()}"

        val base="""
            V21 DEVICE DIAGNOSTICS  ·  点击此页关闭

            Device             : ${Build.MANUFACTURER} ${Build.MODEL}
            Android            : ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}
            Real Resolution    : ${real.widthPixels} × ${real.heightPixels}
            App Metrics        : ${app.widthPixels} × ${app.heightPixels}
            Root View          : ${dashboard.width} × ${dashboard.height}
            Native Design      : 1920 × 1080
            Native Scale       : ${"%.4f".format(Locale.US,dashboard.getDesignScale())}
            Stage Offset       : ${"%.1f".format(Locale.US,dashboard.getStageOffsetX())}, ${"%.1f".format(Locale.US,dashboard.getStageOffsetY())}

            density            : ${"%.4f".format(Locale.US,metrics.density)}
            densityDpi         : ${metrics.densityDpi}
            scaledDensity      : ${"%.4f".format(Locale.US,metrics.scaledDensity)}
            fontScale          : ${"%.4f".format(Locale.US,fontScale)}
            xdpi / ydpi        : ${"%.1f".format(Locale.US,metrics.xdpi)} / ${"%.1f".format(Locale.US,metrics.ydpi)}
            System Bars        : $insetsText

            Touch Multi        : $multiBasic
            Touch Distinct     : $multiDistinct
            Touch JazzHand     : $multiJazz
            Model Touch        : $modelTouch
            Thermal Touch      : $thermalTouch

            WebView Package    : $webPkg
            WebView UA         : ${WebSettings.getDefaultUserAgent(this)}
            Legacy WebView     : 正在读取 innerWidth / innerHeight / DPR …
        """.trimIndent()

        val overlay=TextView(this).apply {
            text=base
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xEE202830.toInt())
            setPadding(48,32,48,32)
            gravity=Gravity.CENTER_VERTICAL
            textSize=15f
            typeface=android.graphics.Typeface.MONOSPACE
            setOnClickListener { closeDiagnostics() }
        }
        diagnosticOverlay=overlay
        addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))

        val probe=WebView(this).apply {
            visibility=View.INVISIBLE
            isClickable=false
            settings.javaScriptEnabled=true
            settings.textZoom=100
            settings.useWideViewPort=false
            settings.loadWithOverviewMode=false
            webViewClient=object:WebViewClient(){
                override fun onPageFinished(view:WebView,url:String?) {
                    view.evaluateJavascript("JSON.stringify({w:window.innerWidth,h:window.innerHeight,dpr:window.devicePixelRatio,sw:screen.width,sh:screen.height})") { value ->
                        val clean=value?.replace("\\\"","\"")?.trim('"') ?: "unavailable"
                        overlay.text=base.replace("正在读取 innerWidth / innerHeight / DPR …",clean)
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                    }
                }
            }
            webChromeClient=WebChromeClient()
            loadDataWithBaseURL(null,"<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body></body></html>","text/html","UTF-8",null)
        }
        addContentView(probe,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun readInsets():String {
        val wi=window.decorView.rootWindowInsets ?: return "Unavailable"
        return if(Build.VERSION.SDK_INT>=30) {
            val i=wi.getInsets(WindowInsets.Type.systemBars())
            "L${i.left} T${i.top} R${i.right} B${i.bottom} px"
        } else {
            @Suppress("DEPRECATION")
            "L${wi.systemWindowInsetLeft} T${wi.systemWindowInsetTop} R${wi.systemWindowInsetRight} B${wi.systemWindowInsetBottom} px"
        }
    }

    private fun closeDiagnostics() {
        diagnosticOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        diagnosticOverlay=null
        enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        if(modelPlaying) animatedModel?.start() ?: fallbackPlayer?.start()
        enterImmersiveMode()
    }

    override fun onPause() {
        animatedModel?.stop()
        fallbackPlayer?.stop()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* kiosk mode */ }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        animatedModel?.stop()
        fallbackPlayer?.release()
        thermals.forEach { it?.bitmap?.recycle() }
        super.onDestroy()
    }
}
