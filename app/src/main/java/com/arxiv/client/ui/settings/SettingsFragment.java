package com.arxiv.client.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.arxiv.client.data.db.ArxivDbHelper;
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
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        switchDarkMode.setChecked(isDarkMode);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean currentlySaved = prefs.getBoolean("dark_mode", false);
            if (isChecked != currentlySaved) {
                prefs.edit().putBoolean("dark_mode", isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        View cardDarkMode = view.findViewById(R.id.card_dark_mode);
        if (cardDarkMode != null) {
            cardDarkMode.setOnClickListener(v -> switchDarkMode.toggle());
        }

        // Cache size display
        updateCacheSizeText();

        // Clear cache
        btnClearCache.setOnClickListener(v -> {
            DownloadService.clearPdfCache(requireContext());
            ArxivDbHelper.getInstance(requireContext()).clearAllLocalPdfPaths();
            updateCacheSizeText();
            Toast.makeText(requireContext(), "Cleared PDF cache storage!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCacheSizeText();
    }

    private void updateCacheSizeText() {
        if (!isAdded()) return;
        long bytes = DownloadService.getCacheSize(requireContext());
        double mb = bytes / (1024.0 * 1024.0);
        tvCacheSize.setText(String.format(java.util.Locale.US, "%.2f MB (Offline cached PDFs)", mb));
    }
}
