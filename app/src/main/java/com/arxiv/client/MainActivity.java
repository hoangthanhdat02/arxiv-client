package com.arxiv.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
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

    private static final String TAG_HOME = "home";
    private static final String TAG_SEARCH = "search";
    private static final String TAG_LIBRARY = "library";
    private static final String TAG_SETTINGS = "settings";
    private static final String KEY_ACTIVE_TAG = "key_active_tag";

    private BottomNavigationView bottomNavigation;

    private Fragment homeFragment;
    private Fragment searchFragment;
    private Fragment libraryFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;
    private String currentActiveTag = TAG_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved Dark Mode preference before super.onCreate
        SharedPreferences prefs = getSharedPreferences("arxiv_prefs", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        int targetMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);

        initFragments(savedInstanceState);
        setupNavigation();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACTIVE_TAG, currentActiveTag);
    }

    private void initFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            searchFragment = new SearchFragment();
            libraryFragment = new LibraryFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, TAG_SETTINGS).hide(settingsFragment)
                    .add(R.id.fragment_container, libraryFragment, TAG_LIBRARY).hide(libraryFragment)
                    .add(R.id.fragment_container, searchFragment, TAG_SEARCH).hide(searchFragment)
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commitAllowingStateLoss();

            activeFragment = homeFragment;
            currentActiveTag = TAG_HOME;
        } else {
            currentActiveTag = savedInstanceState.getString(KEY_ACTIVE_TAG, TAG_HOME);

            homeFragment = fm.findFragmentByTag(TAG_HOME);
            searchFragment = fm.findFragmentByTag(TAG_SEARCH);
            libraryFragment = fm.findFragmentByTag(TAG_LIBRARY);
            settingsFragment = fm.findFragmentByTag(TAG_SETTINGS);

            if (homeFragment == null) homeFragment = new HomeFragment();
            if (searchFragment == null) searchFragment = new SearchFragment();
            if (libraryFragment == null) libraryFragment = new LibraryFragment();
            if (settingsFragment == null) settingsFragment = new SettingsFragment();

            if (TAG_SEARCH.equals(currentActiveTag)) {
                activeFragment = searchFragment;
            } else if (TAG_LIBRARY.equals(currentActiveTag)) {
                activeFragment = libraryFragment;
            } else if (TAG_SETTINGS.equals(currentActiveTag)) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = homeFragment;
                currentActiveTag = TAG_HOME;
            }

            // Ensure only the active fragment is visible and others are hidden
            FragmentTransaction ft = fm.beginTransaction();
            Fragment[] all = {homeFragment, searchFragment, libraryFragment, settingsFragment};
            for (Fragment f : all) {
                if (f.isAdded()) {
                    if (f == activeFragment) {
                        ft.show(f);
                    } else {
                        ft.hide(f);
                    }
                }
            }
            ft.commitAllowingStateLoss();

            // Sync BottomNavigationView selected tab
            int selectedNavId;
            if (TAG_SEARCH.equals(currentActiveTag)) {
                selectedNavId = R.id.nav_search;
            } else if (TAG_LIBRARY.equals(currentActiveTag)) {
                selectedNavId = R.id.nav_library;
            } else if (TAG_SETTINGS.equals(currentActiveTag)) {
                selectedNavId = R.id.nav_settings;
            } else {
                selectedNavId = R.id.nav_home;
            }
            if (bottomNavigation.getSelectedItemId() != selectedNavId) {
                bottomNavigation.setSelectedItemId(selectedNavId);
            }
        }
    }

    private void setupNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment, TAG_HOME);
                return true;
            } else if (itemId == R.id.nav_search) {
                switchFragment(searchFragment, TAG_SEARCH);
                return true;
            } else if (itemId == R.id.nav_library) {
                switchFragment(libraryFragment, TAG_LIBRARY);
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchFragment(settingsFragment, TAG_SETTINGS);
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment targetFragment, String targetTag) {
        if (targetFragment == null || activeFragment == targetFragment) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        if (activeFragment != null && activeFragment.isAdded()) {
            ft.hide(activeFragment);
        }

        if (!targetFragment.isAdded()) {
            ft.add(R.id.fragment_container, targetFragment, targetTag);
        } else {
            ft.show(targetFragment);
        }

        ft.commitAllowingStateLoss();
        activeFragment = targetFragment;
        currentActiveTag = targetTag;
    }
}
