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

        prefs = requireContext().getSharedPreferences("arxiv_prefs", Context.MODE_PRIVATE);

        // Dark mode setup
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (currentNightMode == Configuration.UI_MODE_NIGHT_YES);
        switchDarkMode.setChecked(isNight);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // Cache size display
        updateCacheSizeText();

        // Clear cache
        btnClearCache.setOnClickListener(v -> {
            DownloadService.clearPdfCache(requireContext());
            updateCacheSizeText();
            Toast.makeText(requireContext(), "Đã dọn sạch bộ nhớ cache PDF trên máy ảo!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    private void updateCacheSizeText() {
        long bytes = DownloadService.getCacheSize(requireContext());
        double mb = bytes / (1024.0 * 1024.0);
        tvCacheSize.setText(String.format("%.2f MB (Tệp PDF ngoại tuyến trong máy ảo)", mb));
    }
}
