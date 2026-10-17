package com.arxiv.client.ui.base;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

public abstract class DebounceTextWatcher implements TextWatcher {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private final long delayMs;

    public DebounceTextWatcher(long delayMs) {
        this.delayMs = delayMs;
    }

    public DebounceTextWatcher() {
        this(500); // 500ms default debounce per project spec
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
        runnable = () -> onDebouncedTextChange(s.toString().trim());
        handler.postDelayed(runnable, delayMs);
    }

    public abstract void onDebouncedTextChange(String text);
}
