package com.fightingjoey.oneurl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * 只有一个功能：第一次打开时问一个网址，存到本地，之后每次打开直接加载它。
 */
public class MainActivity extends Activity {

    private static final String PREFS = "oneurl";
    private static final String KEY_URL = "start_url";

    private WebView web;
    private boolean errorShown = false;

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 状态栏压成纯黑。主题里已经写了 statusBarColor，
     * 这里再设一遍是兜底：少数 ROM 会按自己的配色重刷状态栏。
     */
    private void applyBlackStatusBar() {
        Window win = getWindow();
        // 让状态栏颜色由窗口决定，而不是盖一层半透明遮罩
        win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        win.setStatusBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 光有黑底不够：浅色主题会带 SYSTEM_UI_FLAG_LIGHT_STATUS_BAR，
            // 那是"深色图标配浅色背景"的标志，黑底上必须去掉
            View decor = win.getDecorView();
            decor.setSystemUiVisibility(
                    decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyBlackStatusBar();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(Color.WHITE);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                errorShown = false;
            }

            // 用旧签名：它在所有 API 级上都会被调到（新版默认实现会转调它），
            // 而新签名要 API 23+ 的 WebResourceError，minSdk 21 上不保险。
            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (errorShown) return;
                errorShown = true;
                offerFix(failingUrl, description);
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String url = prefs().getString(KEY_URL, null);
        if (TextUtils.isEmpty(url)) {
            promptForUrl(false);
        } else {
            web.loadUrl(url);
        }
    }

    /** 问一次网址。canCancel=false 时是首次启动，必须填。 */
    private void promptForUrl(boolean canCancel) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("example.com 或 192.168.1.10:8080");
        String current = prefs().getString(KEY_URL, null);
        if (!TextUtils.isEmpty(current)) {
            input.setText(current);
            input.setSelection(current.length());
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("输入网址")
                .setMessage("只填这一次，以后打开就直接跳到这个网址。")
                .setView(input)
                // 传 null：点击后不自动关闭，留给下面自己判断网址是否有效
                .setPositiveButton("保存并打开", null);
        if (canCancel) {
            b.setNegativeButton("取消", null);
        }
        final AlertDialog dialog = b.create();
        dialog.setCancelable(canCancel);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // 自己接管点击：网址不合法时不要让对话框先关掉
                View ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (ok != null) {
                    ok.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String url = normalize(input.getText().toString());
                            if (url == null) {
                                Toast.makeText(MainActivity.this, "请先输入网址", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            prefs().edit().putString(KEY_URL, url).apply();
                            errorShown = false;
                            dialog.dismiss();
                            web.loadUrl(url);
                        }
                    });
                }
                input.requestFocus();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
        dialog.show();
    }

    /**
     * 补全协议。局域网 IP / localhost 走 http（自签 https 基本连不上），
     * 其余按 https。已经写了协议的（http://、https://、ftp:// …）原样保留。
     */
    static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.matches("(?i)^[a-z][a-z0-9+.\\-]*://.*")) return s;

        String host = s;
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?")
                || host.toLowerCase().startsWith("localhost")) {
            return "http://" + s;
        }
        return "https://" + s;
    }

    /** 打不开时给个出口，否则网址错了就永远卡死在这一页。 */
    private void offerFix(final String failingUrl, String description) {
        String msg = (TextUtils.isEmpty(failingUrl) ? "" : failingUrl + "\n\n")
                + (TextUtils.isEmpty(description) ? "连接失败" : description);
        new AlertDialog.Builder(this)
                .setTitle("打不开这个网址")
                .setMessage(msg)
                .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        errorShown = false;
                        web.reload();
                    }
                })
                .setNegativeButton("修改网址", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        promptForUrl(true);
                    }
                })
                .setNeutralButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(true)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
