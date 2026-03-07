package com.apertureconcepts.cameo.proxyfix;

import com.nomagic.magicdraw.plugins.Plugin;

public class ProxyFixPlugin extends Plugin {

    @Override
    public void init() {
        // Validation rules are registered as binary implementations in the model.
    }

    @Override
    public boolean close() {
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
