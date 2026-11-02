package com.meuapp.launcher;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppsPlugin.class);
        super.onCreate(savedInstanceState);
        // Launcher nunca fecha no "voltar": avisa a pagina (fecha janelas/painel) e fica na tela inicial.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().post(() ->
                        getBridge().getWebView().evaluateJavascript(
                            "window.dispatchEvent(new Event('launcherback'))", null));
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) AppsPlugin.bringOpenToFront(this);
    }
}
