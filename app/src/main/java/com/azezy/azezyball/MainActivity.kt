package com.azezy.azezyball

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.azezy.azezyball.game.GameManager
import com.azezy.azezyball.game.ScoreEvent
import com.azezy.azezyball.sound.SoundEngine
import com.azezy.azezyball.view.ControlMode
import com.azezy.azezyball.view.SoccerGLSurfaceView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: SoccerGLSurfaceView
    private lateinit var gameManager: GameManager
    private lateinit var soundEngine: SoundEngine

    private lateinit var tvScore: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvControlHint: TextView
    private lateinit var bottomHintCard: LinearLayout

    private lateinit var topBar: View
    private lateinit var btnSound: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var celebrationBanner: LinearLayout
    private lateinit var tvBannerTitle: TextView
    private lateinit var tvBannerSub: TextView

    private var isHintDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        gameManager = GameManager(this)
        soundEngine = SoundEngine(this)

        initViews()
        setupInsets()
        setupListeners()
        setupAutoDismissHint()
        updateDashboard(gameManager.currentScore, gameManager.bestScore, gameManager.currentStreak)
    }

    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.init(gameManager, soundEngine)

        topBar = findViewById(R.id.topBar)
        tvScore = findViewById(R.id.tvScore)
        tvStreak = findViewById(R.id.tvStreak)
        tvDistance = findViewById(R.id.tvDistance)
        tvControlHint = findViewById(R.id.tvControlHint)
        bottomHintCard = findViewById(R.id.bottomHintCard)

        btnSound = findViewById(R.id.btnSound)
        btnSettings = findViewById(R.id.btnSettings)

        celebrationBanner = findViewById(R.id.celebrationBanner)
        tvBannerTitle = findViewById(R.id.tvBannerTitle)
        tvBannerSub = findViewById(R.id.tvBannerSub)
    }

    private fun setupInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            val topPadding = insets.top + (10 * resources.displayMetrics.density).toInt()
            topBar.setPadding(
                topBar.paddingLeft,
                topPadding.coerceAtLeast((48 * resources.displayMetrics.density).toInt()),
                topBar.paddingRight,
                topBar.paddingBottom
            )
            windowInsets
        }
    }

    private fun setupListeners() {
        // Quick Sound toggle
        btnSound.setOnClickListener {
            soundEngine.isSoundEnabled = !soundEngine.isSoundEnabled
            if (soundEngine.isSoundEnabled) {
                btnSound.setImageResource(R.drawable.ic_volume_up)
                Toast.makeText(this, "Âm thanh: BẬT 🔊", Toast.LENGTH_SHORT).show()
            } else {
                btnSound.setImageResource(R.drawable.ic_volume_off)
                Toast.makeText(this, "Âm thanh: TẮT 🔇", Toast.LENGTH_SHORT).show()
            }
        }

        // Settings / Options Menu
        btnSettings.setOnClickListener {
            showSettingsMenu()
        }

        // Game Manager callbacks
        gameManager.onScoreChanged = { score, best, streak ->
            runOnUiThread {
                updateDashboard(score, best, streak)
            }
        }

        gameManager.onGoalCelebration = { event ->
            runOnUiThread {
                showGoalCelebration(event)
            }
        }

        gameManager.onMissBanner = {
            runOnUiThread {
                showMissBanner()
            }
        }
    }

    private fun setupAutoDismissHint() {
        // Auto hide after 5 seconds
        bottomHintCard.postDelayed({
            dismissHint()
        }, 5000)

        // Or immediately hide on first touch
        glSurfaceView.onFirstTouch = {
            runOnUiThread {
                dismissHint()
            }
        }
    }

    private fun dismissHint() {
        if (isHintDismissed || bottomHintCard.visibility != View.VISIBLE) return
        isHintDismissed = true

        val fadeOut = ObjectAnimator.ofFloat(bottomHintCard, "alpha", 1.0f, 0f).apply {
            duration = 350
        }
        fadeOut.start()
        bottomHintCard.postDelayed({
            bottomHintCard.visibility = View.GONE
        }, 350)
    }

    private fun updateDashboard(score: Int, best: Int, streak: Int) {
        tvScore.text = String.format(Locale.US, "%,d", score)
        tvStreak.text = "🔥 x$streak"
        tvDistance.text = String.format(Locale.US, "🎯 %.1fm", gameManager.currentDistance)
    }

    private fun showSettingsMenu() {
        val keeperState = if (glSurfaceView.renderer.ballPhysics.goalkeeperEnabled) "TẮT" else "BẬT"
        val modeState = if (glSurfaceView.renderer.controlMode == ControlMode.SWIPE_FLICK) "Chuyển sang Kéo ngắm (Slingshot)" else "Chuyển sang Vuốt tự do (Swipe & Curve)"

        val options = arrayOf(
            "🧤 Thủ môn chắn bóng: $keeperState",
            "🎮 Chế độ sút: $modeState",
            "👑 Kỷ lục cao nhất: ${String.format(Locale.US, "%,d điểm", gameManager.bestScore)}",
            "📖 Hướng dẫn cách chơi",
            "🔄 Làm mới trận đấu (Reset)"
        )

        AlertDialog.Builder(this)
            .setTitle("⚙️ TÙY CHỌN TRẬN ĐẤU")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        glSurfaceView.renderer.ballPhysics.goalkeeperEnabled = !glSurfaceView.renderer.ballPhysics.goalkeeperEnabled
                        val msg = if (glSurfaceView.renderer.ballPhysics.goalkeeperEnabled) "Thủ môn: ĐÃ BẬT 🧤" else "Thủ môn: ĐÃ TẮT"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        if (glSurfaceView.renderer.controlMode == ControlMode.SWIPE_FLICK) {
                            glSurfaceView.renderer.controlMode = ControlMode.SLINGSHOT_AIM
                            tvControlHint.text = "Kéo bóng lùi lại để ngắm quỹ đạo và thả tay để sút!"
                            Toast.makeText(this, "Chế độ: Ngắm bắn kéo thả (Slingshot)", Toast.LENGTH_SHORT).show()
                        } else {
                            glSurfaceView.renderer.controlMode = ControlMode.SWIPE_FLICK
                            tvControlHint.text = getString(R.string.swipe_to_kick)
                            Toast.makeText(this, "Chế độ: Vuốt tự do (Swipe & Curve)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        Toast.makeText(this, "Kỷ lục chuỗi: x${gameManager.bestStreak} bàn liên tiếp!", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        showHelpDialog()
                    }
                    4 -> {
                        gameManager.resetGame()
                        glSurfaceView.renderer.ballPhysics.reset()
                        Toast.makeText(this, "Đã làm mới trận đấu!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showGoalCelebration(event: ScoreEvent) {
        celebrationBanner.setBackgroundResource(R.drawable.bg_banner_goal)
        celebrationBanner.visibility = View.VISIBLE
        tvBannerTitle.text = event.title
        tvBannerTitle.setTextColor(Color.rgb(253, 224, 71)) // Bright Shiny Gold
        tvBannerSub.text = String.format(Locale.US, "+%d ĐIỂM (Cự ly %.1fm)", event.pointsAdded, event.distanceMeters)
        tvBannerSub.setTextColor(Color.WHITE)

        celebrationBanner.scaleX = 0.3f
        celebrationBanner.scaleY = 0.3f
        celebrationBanner.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(celebrationBanner, "scaleX", 0.3f, 1.05f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(celebrationBanner, "scaleY", 0.3f, 1.05f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 380
            interpolator = OvershootInterpolator(2.0f)
            start()
        }

        celebrationBanner.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 1.0f, 0f).apply {
                duration = 280
            }
            fadeOut.start()
            celebrationBanner.postDelayed({
                celebrationBanner.visibility = View.GONE
            }, 280)
        }, 1800)
    }

    private fun showMissBanner() {
        celebrationBanner.setBackgroundResource(R.drawable.bg_banner_miss)
        celebrationBanner.visibility = View.VISIBLE
        tvBannerTitle.text = "❌ KHÔNG VÀO!"
        tvBannerTitle.setTextColor(Color.WHITE)
        tvBannerSub.text = "Bóng ra ngoài! Chuỗi bàn thắng bị ngắt."
        tvBannerSub.setTextColor(Color.rgb(254, 226, 226))

        celebrationBanner.scaleX = 0.8f
        celebrationBanner.scaleY = 0.8f
        celebrationBanner.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(celebrationBanner, "scaleX", 0.8f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(celebrationBanner, "scaleY", 0.8f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 280
            start()
        }

        celebrationBanner.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 1.0f, 0f).apply {
                duration = 240
            }
            fadeOut.start()
            celebrationBanner.postDelayed({
                celebrationBanner.visibility = View.GONE
            }, 240)
        }, 1200)
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("🏆 HƯỚNG DẪN CHƠI AZEZY BALL 3D")
            .setMessage(
                "⚽ 1. CÁCH SÚT BÓNG:\n" +
                "• Chế độ Vuốt: Đặt ngón tay lên bóng và vuốt thẳng/cong về phía khung thành vàng. Vuốt cong hình vòng cung để tạo đường bóng xoáy bẻ hướng (Magnus effect)!\n" +
                "• Chế độ Kéo thả: Kéo bóng lùi về sau để căn góc và lực với đường line 3D hiển thị trước.\n\n" +
                "🌟 2. TÍNH ĐIỂM & THỬ THÁCH:\n" +
                "• Ghi bàn từ cự ly càng xa nhận càng nhiều điểm thưởng.\n" +
                "• Sút trúng góc chữ A (Top Corner) được cộng thêm +150 điểm thưởng!\n" +
                "• Chuỗi ghi bàn liên tiếp kích hoạt hệ số nhân điểm (Combo x2, x3, x5)!\n" +
                "• Bật nút Thủ môn trong Menu để tăng độ thử thách!"
            )
            .setPositiveButton("Đã hiểu, Bắt đầu!", null)
            .show()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundEngine.release()
    }
}
