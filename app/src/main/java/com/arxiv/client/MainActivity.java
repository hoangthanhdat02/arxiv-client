package com.arxiv.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.arxiv.client.ui.home.HomeFragment;
import com.arxiv.client.ui.library.LibraryFragment;
import com.arxiv.client.ui.search.SearchFragment;
import com.arxiv.client.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    private final Fragment homeFragment = new HomeFragment();
    private final Fragment searchFragment = new SearchFragment();
    private final Fragment libraryFragment = new LibraryFragment();
    private final Fragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved Dark Mode preference before super.onCreate
        SharedPreferences prefs = getSharedPreferences("arxiv_prefs", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);

        initFragments(savedInstanceState);
        setupNavigation();
    }

    private void initFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, "4").hide(settingsFragment)
                    .add(R.id.fragment_container, libraryFragment, "3").hide(libraryFragment)
                    .add(R.id.fragment_container, searchFragment, "2").hide(searchFragment)
                    .add(R.id.fragment_container, homeFragment, "1")
                    .commit();
        }
    }

    private void setupNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                switchFragment(searchFragment);
                return true;
            } else if (itemId == R.id.nav_library) {
                switchFragment(libraryFragment);
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchFragment(settingsFragment);
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment targetFragment) {
        if (activeFragment != targetFragment) {
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .hide(activeFragment)
                    .show(targetFragment)
                    .commit();
            activeFragment = targetFragment;
        }
    }
}
