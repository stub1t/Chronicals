package io.github.stub1t.chronicals;

import android.content.res.Configuration;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugins that live in the app itself are registered before the bridge starts.
        registerPlugin(LocationPlugin.class);
        super.onCreate(savedInstanceState);
        // The WebView shows this colour until the page paints. Follow the system mode so a dark phone gets no light flash after the start screen.
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        getBridge().getWebView().setBackgroundColor(night ? 0xFF18151A : 0xFFFBF7F1);
    }
}
