package com.dontstop.tracking

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.dontstop.R
import com.dontstop.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayController(private val context: Context, private val scope: CoroutineScope) {
    private val window = context.getSystemService(WindowManager::class.java)
    private val store = SettingsStore(context)
    private var ball: FrameLayout? = null
    private var panel: View? = null
    private var downX = 0f
    private var downY = 0f
    private var initialX = 0
    private var initialY = 0
    private var ballDockedLeft = false
    private var detailText = "DONTSTOP dekh raha hai"
    @Volatile private var visibilityVersion = 0L
    private var lastTemplatePath: String? = null

    fun show(details: String) {
        detailText = details
        if (!Settings.canDrawOverlays(context)) return
        val version = ++visibilityVersion
        scope.launch(Dispatchers.Main) {
            if (version != visibilityVersion || ball != null) return@launch
            val saved = store.overlayPosition()
            val selectedTemplate = store.get().assistantBall
            if (version != visibilityVersion || ball != null) return@launch
            val params = layout(BALL_WIDTH_DP, BALL_HEIGHT_DP).apply {
                x = dockX(saved.first)
                y = saved.second.coerceIn(0, maxY())
            }
            ballDockedLeft = params.x < context.resources.displayMetrics.widthPixels / 2
            ball = FrameLayout(context).apply {
                contentDescription = context.getString(R.string.overlay_description)
                clipChildren = false
                clipToPadding = false
                addView(templateImage(selectedTemplate), FrameLayout.LayoutParams(dp(BALL_WIDTH_DP - 8), dp(BALL_HEIGHT_DP - 8)).apply {
                    gravity = Gravity.CENTER
                })
                setOnTouchListener { _, event -> onTouch(event, params) }
            }.also { window.addView(it, params) }
        }
    }

    fun update(details: String) {
        if (detailText == details) return
        detailText = details
        scope.launch(Dispatchers.Main) {
            // A counter change means the user resumed scrolling. Collapse the expanded
            // score so it never remains over the Reel/Short after a swipe.
            panel?.let { runCatching { window.removeView(it) } }
            panel = null
        }
    }

    fun hide() {
        val version = ++visibilityVersion
        scope.launch(Dispatchers.Main) {
            if (version != visibilityVersion) return@launch
            panel?.let { runCatching { window.removeView(it) } }; panel = null
            ball?.let { runCatching { window.removeView(it) } }; ball = null
        }
    }

    fun refreshTemplate() {
        scope.launch(Dispatchers.Main) {
            val selectedTemplate = store.get().assistantBall
            ball?.let { container ->
                container.removeViewAt(0)
                container.addView(templateImage(selectedTemplate), 0, FrameLayout.LayoutParams(dp(BALL_WIDTH_DP - 8), dp(BALL_HEIGHT_DP - 8)).apply {
                    gravity = Gravity.CENTER
                })
            }
        }
    }

    private fun onTouch(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; initialX = params.x; initialY = params.y }
            MotionEvent.ACTION_MOVE -> {
                params.x = (initialX + (event.rawX - downX).toInt()).coerceIn(-edgeHidePx(), maxX() + edgeHidePx())
                params.y = (initialY + (event.rawY - downY).toInt()).coerceIn(0, maxY())
                ball?.let { window.updateViewLayout(it, params) }
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) togglePanel()
                params.x = dockX(params.x)
                ballDockedLeft = params.x < context.resources.displayMetrics.widthPixels / 2
                (ball?.getChildAt(0) as? ImageView)?.rotation = if (ballDockedLeft) 90f else -90f
                params.y = params.y.coerceIn(0, maxY())
                ball?.let { window.updateViewLayout(it, params) }
                scope.launch { store.setOverlayPosition(params.x, params.y) }
            }
        }
        return true
    }

    private fun togglePanel() {
        panel?.let { window.removeView(it); panel = null; return }
        panel = TextView(context).apply {
            text = detailText
            textSize = 18f
            setTextColor(0xFF17132C.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(4).toFloat(), 1f)
            background = roundedRect(0xFFFFBC00.toInt(), 0xFF1B143E.toInt(), 3, 25)
            setPadding(dp(20), dp(17), dp(20), dp(17))
            setOnClickListener { panel?.let { window.removeView(it) }; panel = null }
        }.also { view ->
            val screenWidth = context.resources.displayMetrics.widthPixels
            val panelWidth = dp(PANEL_WIDTH_DP)
            window.addView(view, layout(PANEL_WIDTH_DP, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                x = if (ballDockedLeft) dp(48) else (screenWidth - panelWidth - dp(48)).coerceAtLeast(0)
                y = dp(160)
            })
        }
    }

    private fun layout(widthDp: Int, heightDp: Int) = WindowManager.LayoutParams(
        if (widthDp > 0) dp(widthDp) else widthDp, if (heightDp > 0) dp(heightDp) else heightDp,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun templateImage(selectedTemplate: String) = ImageView(context).apply {
        // Preserve the template silhouette and transparency, without a circular mask.
        scaleType = ImageView.ScaleType.FIT_CENTER
        rotation = if (ballDockedLeft) 90f else -90f
        val templates = context.assets.list(TEMPLATE_DIRECTORY).orEmpty()
            .filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".webp", true) }
            .map { "$TEMPLATE_DIRECTORY/$it" }
        val choices = if (templates.size > 1) templates.filterNot { it == lastTemplatePath } else templates
        val chosen = templates.firstOrNull { it == "$TEMPLATE_DIRECTORY/$selectedTemplate" }
            ?: choices.randomOrNull()
        val bitmap = chosen?.let { path -> runCatching {
            context.assets.open(path).use(BitmapFactory::decodeStream)
        }.getOrNull() }
        if (bitmap != null) {
            setImageBitmap(bitmap)
            lastTemplatePath = chosen
        } else {
            setImageResource(R.drawable.dontstop_ball)
        }
    }

    private fun roundedRect(color: Int, strokeColor: Int, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeDp), strokeColor)
    }

    private fun dockX(currentX: Int): Int = if (currentX < context.resources.displayMetrics.widthPixels / 2) {
        -edgeHidePx()
    } else {
        maxX() + edgeHidePx()
    }

    private fun maxX() = context.resources.displayMetrics.widthPixels - dp(BALL_WIDTH_DP)
    private fun maxY() = context.resources.displayMetrics.heightPixels - dp(BALL_HEIGHT_DP + 72)
    private fun edgeHidePx() = dp(EDGE_HIDE_DP)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        // A small, partly hidden companion reads as a character peeking from the
        // app edge, rather than as a generic floating action button.
        private const val BALL_WIDTH_DP = 126
        private const val BALL_HEIGHT_DP = 96
        private const val EDGE_HIDE_DP = 47
        private const val PANEL_WIDTH_DP = 250
        private const val TEMPLATE_DIRECTORY = "assistant_balls"
    }
}
