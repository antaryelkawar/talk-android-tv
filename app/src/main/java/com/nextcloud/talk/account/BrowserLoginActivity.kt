/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import autodagger.AutoInjector
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nextcloud.talk.R
import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.viewmodels.BrowserLoginActivityViewModel
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ActivityWebViewLoginBinding
import com.nextcloud.talk.utils.TvUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import kotlinx.coroutines.launch
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class BrowserLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityWebViewLoginBinding

    @Inject
    lateinit var viewModel: BrowserLoginActivityViewModel

    private var reauthorizeAccount = false

    private var isTvMode = false
    private var tvQrShown = false
    private var tvLoginUrl: String? = null
    private var tvWebView: WebView? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isTvMode) {
                val webViewContainer = findViewById<LinearLayout>(R.id.tv_webview_login_container)
                if (webViewContainer?.visibility == View.VISIBLE) {
                    if (tvWebView?.canGoBack() == true) {
                        tvWebView?.goBack()
                        return
                    }
                    showTvQrScreen()
                    return
                }
            }
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        binding = ActivityWebViewLoginBinding.inflate(layoutInflater)

        isTvMode = TvUtils.isTvMode(this)

        if (!isTvMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContentView(binding.root)
        actionBar?.hide()
        initSystemBars()
        initViews()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        handleIntent()
        observe()
    }

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.initialLoginRequestState.collect { state ->
                    when (state) {
                        BrowserLoginActivityViewModel.InitialLoginViewState.InitialLoginRequestError -> {
                            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_SHORT).show()
                        }

                        is BrowserLoginActivityViewModel.InitialLoginViewState.InitialLoginRequestSuccess -> {
                            if (isTvMode) {
                                if (!tvQrShown) {
                                    tvQrShown = true
                                    tvLoginUrl = state.loginUrl
                                    showQrCode(state.loginUrl)
                                    viewModel.handleWebBrowserLogin()
                                }
                            } else {
                                if (viewModel.waitingForBrowserState.value) {
                                    viewModel.setWaitingForBrowser(false)
                                    viewModel.handleWebBrowserLogin()
                                } else {
                                    viewModel.setWaitingForBrowser(true)
                                    launchDefaultWebBrowser(state.loginUrl)
                                }
                            }
                        }

                        BrowserLoginActivityViewModel.InitialLoginViewState.None -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.postLoginState.collect { state ->
                    when (state) {
                        BrowserLoginActivityViewModel.PostLoginViewState.None -> {}

                        is BrowserLoginActivityViewModel.PostLoginViewState.PostLoginContinue -> {
                            if (!state.data.isEmpty) {
                                startAccountVerification(state.data)
                            }
                        }

                        BrowserLoginActivityViewModel.PostLoginViewState.PostLoginError -> {
                            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_SHORT).show()
                        }

                        BrowserLoginActivityViewModel.PostLoginViewState.PostLoginRestartApp -> {
                            restartApp()
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent() {
        val extras = intent.extras!!
        val baseUrl = extras.getString(KEY_BASE_URL)

        if (extras.containsKey(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)) {
            reauthorizeAccount = extras.getBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)
        }

        if (extras.containsKey(BundleKeys.KEY_FROM_QR)) {
            val uri = extras.getString(BundleKeys.KEY_FROM_QR)!!

            if (uri.startsWith(LoginRepository.ONE_TIME_PREFIX)) {
                viewModel.loginWithOTPQR(uri, reauthorizeAccount)
            } else {
                viewModel.loginWithQR(uri, reauthorizeAccount)
            }
        } else if (baseUrl != null) {
            viewModel.startWebBrowserLogin(baseUrl, reauthorizeAccount)
        }
    }

    private fun initViews() {
        viewThemeUtils.material.colorMaterialButtonFilledOnPrimary(binding.cancelLoginBtn)
        viewThemeUtils.material.colorProgressBar(binding.progressBar)

        binding.cancelLoginBtn.setOnClickListener {
            viewModel.cancelLogin()
            onBackPressedDispatcher.onBackPressed()
        }

        if (isTvMode) {
            initTvViews()
        }
    }

    private fun initTvViews() {
        val focusColor = resources.getColor(R.color.colorPrimary, null)

        val browserBtn = findViewById<MaterialButton>(R.id.tv_login_with_browser_btn)
        browserBtn?.let {
            it.isFocusable = true
            it.isFocusableInTouchMode = false
            TvUtils.applyTvFocusHighlight(it, focusColor)
            it.setOnClickListener { showTvWebViewScreen() }
            it.requestFocus()
        }

        val backBtn = findViewById<MaterialButton>(R.id.tv_webview_back_btn)
        backBtn?.let {
            it.isFocusable = true
            it.isFocusableInTouchMode = false
            TvUtils.applyTvFocusHighlight(it, focusColor)
            it.setOnClickListener { showTvQrScreen() }
        }

        binding.cancelLoginBtn.isFocusable = true
        binding.cancelLoginBtn.isFocusableInTouchMode = false
        TvUtils.applyTvFocusHighlight(binding.cancelLoginBtn, focusColor)

        setupTvWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupTvWebView() {
        tvWebView = findViewById(R.id.tv_login_webview)
        tvWebView?.let { wv ->
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.setSupportMultipleWindows(false)
            wv.settings.javaScriptCanOpenWindowsAutomatically = true

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            wv.webChromeClient = WebChromeClient()
        }
    }

    private fun showTvWebViewScreen() {
        val url = tvLoginUrl ?: return
        val qrContainer = findViewById<LinearLayout>(R.id.tv_qr_login_container)
        val webViewContainer = findViewById<LinearLayout>(R.id.tv_webview_login_container)

        qrContainer?.visibility = View.GONE
        webViewContainer?.visibility = View.VISIBLE

        tvWebView?.loadUrl(url)
    }

    private fun showTvQrScreen() {
        val qrContainer = findViewById<LinearLayout>(R.id.tv_qr_login_container)
        val webViewContainer = findViewById<LinearLayout>(R.id.tv_webview_login_container)

        webViewContainer?.visibility = View.GONE
        qrContainer?.visibility = View.VISIBLE

        findViewById<MaterialButton>(R.id.tv_login_with_browser_btn)?.requestFocus()
    }

    @Suppress("MagicNumber")
    private fun showQrCode(loginUrl: String) {
        Log.d(TAG, "showQrCode called with URL length: ${loginUrl.length}")
        val qrCodeImage = findViewById<ImageView>(R.id.tv_qr_code_image)
        if (qrCodeImage != null) {
            binding.progressBar.visibility = View.GONE
            val bitmap = generateQrCode(loginUrl, QR_CODE_SIZE)
            if (bitmap != null) {
                qrCodeImage.setImageBitmap(bitmap)
                qrCodeImage.visibility = View.VISIBLE
                Log.d(TAG, "QR code displayed successfully")
            } else {
                Log.e(TAG, "Failed to generate QR code bitmap")
            }
        } else {
            Log.w(TAG, "tv_qr_code_image not found in layout, falling back to browser")
            launchDefaultWebBrowser(loginUrl)
        }
    }

    @Suppress("MagicNumber")
    private fun generateQrCode(content: String, size: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun launchDefaultWebBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun startAccountVerification(bundle: Bundle) {
        val intent = Intent(context, AccountVerificationActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun restartApp() {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    public override fun onDestroy() {
        tvWebView?.destroy()
        super.onDestroy()
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private val TAG = BrowserLoginActivity::class.java.simpleName
        private const val QR_CODE_SIZE = 512
    }
}
