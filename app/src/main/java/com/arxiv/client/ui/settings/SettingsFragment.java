package com.arxiv.client.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.arxiv.client.R;
import com.arxiv.client.data.download.DownloadService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SwitchMaterial switchDarkMode;
    private TextView tvCacheSize;
    private MaterialButton btnClearCache;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        tvCacheSize = view.findViewById(R.id.tv_cache_size);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);

        Context context = getContext();
        if (context != null) {
            prefs = context.getSharedPreferences("arxiv_prefs", Context.MODE_PRIVATE);
        }

        // Dark mode setup
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);

        // Remove listener before setChecked to prevent false triggers during inflation
        switchDarkMode.setOnCheckedChangeListener(null);
        switchDarkMode.setChecked(isNight);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int currentMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean currentlyNight = (currentMode == Configuration.UI_MODE_NIGHT_YES);

            // Avoid loop or duplicate recreation if already in that mode
            if (isChecked == currentlyNight) {
                return;
            }

            if (prefs != null) {
                prefs.edit().putBoolean("dark_mode", isChecked).apply();
            }
            int targetMode = isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });

        // Cache size display
        updateCacheSizeText();

        // Clear cache
        btnClearCache.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null || !isAdded()) return;
            DownloadService.clearPdfCache(ctx);
            updateCacheSizeText();
            Toast.makeText(ctx, "PDF cache cleared successfully!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded() && getContext() != null) {
            updateCacheSizeText();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded() && getContext() != null) {
            updateCacheSizeText();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        switchDarkMode = null;
        tvCacheSize = null;
        btnClearCache = null;
    }

    private void updateCacheSizeText() {
        if (!isAdded() || getContext() == null || tvCacheSize == null) {
            return;
        }
        Context ctx = getContext();
        long bytes = DownloadService.getCacheSize(ctx);
        double mb = bytes / (1024.0 * 1024.0);
        tvCacheSize.setText(String.format(Locale.US, "%.2f MB (Offline PDF files on device)", mb));
    }
}
