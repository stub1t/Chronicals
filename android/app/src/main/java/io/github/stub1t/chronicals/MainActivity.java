package io.github.stub1t.chronicals;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugins that live in the app itself are registered before the bridge starts.
        registerPlugin(LocationPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
