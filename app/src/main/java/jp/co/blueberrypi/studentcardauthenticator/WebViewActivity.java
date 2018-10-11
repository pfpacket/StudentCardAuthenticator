package jp.co.blueberrypi.studentcardauthenticator;

import android.app.Activity;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class WebViewActivity extends Activity {
    static public String EXTRA_STUDENT_CARD_INFO = "EXTRA_STUDENT_CARD_INFO";
    static private String BLUEBERRY_PI_SERVER = "https://10.75.169.98:8000/";
    //static private String BLUEBERRY_PI_SERVER = "https://10.75.73.140:8000/";

    private WebView webView;
    SwipeRefreshLayout swipeRefreshLayout;
    StudentCardInfo info;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.clearCache(true);
        CookieManager.getInstance().removeAllCookie();  /* to remove the login state */
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        info = (StudentCardInfo) this.getIntent().getSerializableExtra(EXTRA_STUDENT_CARD_INFO);

        webView = (WebView) findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        //webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setUseWideViewPort(true);         /* support view port */
        webView.getSettings().setLoadWithOverviewMode(true);    /* show pages with zoomed out */
        //webView.getSettings().setSupportMultipleWindows(true);

        /* To display 'alert'*/
        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                /* Trust all certs without a word :( Should be fixed somehow. */
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.loadUrl(String.format("javascript:window.cardReaderCallback('%s', '%s');", info.systemCode, info.studentNumber));
            }
        });

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        webView.loadUrl(BLUEBERRY_PI_SERVER);

        /* Swipe to reload */
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }
}
