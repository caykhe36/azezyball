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
    private lateinit var tvBestScore: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvControlHint: TextView

    private lateinit var btnSound: ImageButton
    private lateinit var btnKeeper: ImageButton
    private lateinit var btnMode: ImageButton
    private lateinit var btnHelp: ImageButton
    private lateinit var btnReset: ImageButton

    private lateinit var celebrationBanner: LinearLayout
    private lateinit var tvBannerTitle: TextView
    private lateinit var tvBannerSub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()

        setContentView(R.layout.activity_main)

        gameManager = GameManager(this)
        soundEngine = SoundEngine(this)

        initViews()
        setupListeners()
        updateDashboard(gameManager.currentScore, gameManager.bestScore, gameManager.currentStreak)
    }

    private fun initViews() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.init(gameManager, soundEngine)

        tvScore = findViewById(R.id.tvScore)
        tvStreak = findViewById(R.id.tvStreak)
        tvBestScore = findViewById(R.id.tvBestScore)
        tvDistance = findViewById(R.id.tvDistance)
        tvControlHint = findViewById(R.id.tvControlHint)

        btnSound = findViewById(R.id.btnSound)
        btnKeeper = findViewById(R.id.btnKeeper)
        btnMode = findViewById(R.id.btnMode)
        btnHelp = findViewById(R.id.btnHelp)
        btnReset = findViewById(R.id.btnReset)

        celebrationBanner = findViewById(R.id.celebrationBanner)
        tvBannerTitle = findViewById(R.id.tvBannerTitle)
        tvBannerSub = findViewById(R.id.tvBannerSub)
    }

    private fun setupListeners() {
        // Sound toggle
        btnSound.setOnClickListener {
            soundEngine.isSoundEnabled = !soundEngine.isSoundEnabled
            if (soundEngine.isSoundEnabled) {
                btnSound.setImageResource(R.drawable.ic_volume_up)
                Toast.makeText(this, "Âm thanh: BẬT", Toast.LENGTH_SHORT).show()
            } else {
                btnSound.setImageResource(R.drawable.ic_volume_off)
                Toast.makeText(this, "Âm thanh: TẮT", Toast.LENGTH_SHORT).show()
            }
        }

        // Goalkeeper toggle
        btnKeeper.setOnClickListener {
            val enabled = !glSurfaceView.renderer.ballPhysics.goalkeeperEnabled
            glSurfaceView.renderer.ballPhysics.goalkeeperEnabled = enabled
            btnKeeper.setColorFilter(if (enabled) ContextCompat.getColor(this, R.color.gold_primary) else Color.GRAY)
            val msg = if (enabled) "Thủ môn: BẬT 🧤 (Thử thách tăng cao!)" else "Thủ môn: TẮT"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Control Mode toggle
        btnMode.setOnClickListener {
            val currentMode = glSurfaceView.renderer.controlMode
            if (currentMode == ControlMode.SWIPE_FLICK) {
                glSurfaceView.renderer.controlMode = ControlMode.SLINGSHOT_AIM
                tvControlHint.text = "Kéo bóng lùi lại để ngắm quỹ đạo và thả tay để sút!"
                Toast.makeText(this, "Chế độ: Ngắm bắn kéo thả (Slingshot)", Toast.LENGTH_SHORT).show()
            } else {
                glSurfaceView.renderer.controlMode = ControlMode.SWIPE_FLICK
                tvControlHint.text = getString(R.string.swipe_to_kick)
                Toast.makeText(this, "Chế độ: Vuốt tự do (Swipe & Curve)", Toast.LENGTH_SHORT).show()
            }
        }

        // Help dialog
        btnHelp.setOnClickListener {
            showHelpDialog()
        }

        // Reset score
        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Làm mới trận đấu?")
                .setMessage("Bạn có muốn đặt lại điểm số và chuỗi bàn thắng về 0?")
                .setPositiveButton("Đồng ý") { _, _ ->
                    gameManager.resetGame()
                    glSurfaceView.renderer.ballPhysics.reset()
                    Toast.makeText(this, "Đã làm mới trận đấu!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Hủy", null)
                .show()
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

    private fun updateDashboard(score: Int, best: Int, streak: Int) {
        tvScore.text = String.format(Locale.US, "%,d", score)
        tvBestScore.text = String.format(Locale.US, "%,d", best)
        tvStreak.text = "x$streak"
        tvDistance.text = String.format(Locale.US, "Cự ly: %.1fm", gameManager.currentDistance)
    }

    private fun showGoalCelebration(event: ScoreEvent) {
        celebrationBanner.visibility = View.VISIBLE
        tvBannerTitle.text = event.title
        tvBannerTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
        tvBannerSub.text = String.format(Locale.US, "+%d ĐIỂM (Cự ly %.1fm)", event.pointsAdded, event.distanceMeters)

        // Overshoot Scale Animation
        celebrationBanner.scaleX = 0.3f
        celebrationBanner.scaleY = 0.3f
        celebrationBanner.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(celebrationBanner, "scaleX", 0.3f, 1.05f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(celebrationBanner, "scaleY", 0.3f, 1.05f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 450
            interpolator = OvershootInterpolator(2.0f)
            start()
        }

        // Auto hide after 1.8s
        celebrationBanner.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 1.0f, 0f).apply {
                duration = 350
            }
            fadeOut.start()
            celebrationBanner.postDelayed({
                celebrationBanner.visibility = View.GONE
            }, 350)
        }, 1800)
    }

    private fun showMissBanner() {
        celebrationBanner.visibility = View.VISIBLE
        tvBannerTitle.text = getString(R.string.miss)
        tvBannerTitle.setTextColor(ContextCompat.getColor(this, R.color.red_miss))
        tvBannerSub.text = "Bóng ra ngoài khung thành! Chuỗi ghi bàn bị ngắt."

        celebrationBanner.scaleX = 0.8f
        celebrationBanner.scaleY = 0.8f
        celebrationBanner.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(celebrationBanner, "scaleX", 0.8f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(celebrationBanner, "scaleY", 0.8f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            start()
        }

        celebrationBanner.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(celebrationBanner, "alpha", 1.0f, 0f).apply {
                duration = 250
            }
            fadeOut.start()
            celebrationBanner.postDelayed({
                celebrationBanner.visibility = View.GONE
            }, 250)
        }, 1200)
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("🏆 HƯỚNG DẪN CHƠI AZEZY BALL 3D")
            .setMessage(
                "⚽ 1. CÁCH SÚT BÓNG:\n" +
                "• Chế độ Vuốt (Mặc định): Đặt ngón tay lên bóng và vuốt nhanh về phía khung thành vàng. Vuốt cong hình vòng cung để tạo đường bóng xoáy bẻ hướng (Hiệu ứng Magnus)!\n" +
                "• Chế độ Kéo thả: Kéo bóng lùi về sau để căn góc và lực với đường line 3D hiển thị trước.\n\n" +
                "🌟 2. TÍNH ĐIỂM & THỬ THÁCH:\n" +
                "• Ghi bàn từ cự ly càng xa nhận càng nhiều điểm thưởng.\n" +
                "• Sút trúng góc chữ A (Top Corner) được cộng thêm +150 điểm thưởng!\n" +
                "• Chuỗi ghi bàn liên tiếp kích hoạt hệ số nhân điểm (Combo x2, x3, x5)!\n" +
                "• Bật nút Thủ môn để tăng thử thách với thủ môn di chuyển chắn bóng!"
            )
            .setPositiveButton("Đã hiểu, Bắt đầu!", null)
            .show()
    }

    private fun hideSystemUI() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
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
