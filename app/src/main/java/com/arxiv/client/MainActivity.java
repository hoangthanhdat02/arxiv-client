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

    private static final String KEY_SELECTED_NAV = "selected_nav_item_id";

    private BottomNavigationView bottomNavigation;

    private Fragment homeFragment;
    private Fragment searchFragment;
    private Fragment libraryFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved Dark Mode preference explicitly for both YES and NO
        SharedPreferences prefs = getSharedPreferences("arxiv_prefs", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);

        initFragments(savedInstanceState);
        setupNavigation(savedInstanceState);
    }

    private void initFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            searchFragment = new SearchFragment();
            libraryFragment = new LibraryFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, "4").hide(settingsFragment)
                    .add(R.id.fragment_container, libraryFragment, "3").hide(libraryFragment)
                    .add(R.id.fragment_container, searchFragment, "2").hide(searchFragment)
                    .add(R.id.fragment_container, homeFragment, "1")
                    .commit();

            activeFragment = homeFragment;
        } else {
            // Restore references to already attached fragments preserved across recreation
            homeFragment = fm.findFragmentByTag("1");
            if (homeFragment == null) homeFragment = new HomeFragment();

            searchFragment = fm.findFragmentByTag("2");
            if (searchFragment == null) searchFragment = new SearchFragment();

            libraryFragment = fm.findFragmentByTag("3");
            if (libraryFragment == null) libraryFragment = new LibraryFragment();

            settingsFragment = fm.findFragmentByTag("4");
            if (settingsFragment == null) settingsFragment = new SettingsFragment();

            int selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.nav_home);
            if (selectedNavId == R.id.nav_search) {
                activeFragment = searchFragment;
            } else if (selectedNavId == R.id.nav_library) {
                activeFragment = libraryFragment;
            } else if (selectedNavId == R.id.nav_settings) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = homeFragment;
            }
        }
    }

    private void setupNavigation(Bundle savedInstanceState) {
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

        if (savedInstanceState != null) {
            int selectedNavId = savedInstanceState.getInt(KEY_SELECTED_NAV, R.id.nav_home);
            Fragment restoredFragment;
            if (selectedNavId == R.id.nav_search) {
                restoredFragment = searchFragment;
            } else if (selectedNavId == R.id.nav_library) {
                restoredFragment = libraryFragment;
            } else if (selectedNavId == R.id.nav_settings) {
                restoredFragment = settingsFragment;
            } else {
                restoredFragment = homeFragment;
            }
            switchFragment(restoredFragment);
        }
    }

    private void switchFragment(Fragment targetFragment) {
        if (targetFragment == null) return;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        Fragment[] allFragments = {homeFragment, searchFragment, libraryFragment, settingsFragment};
        for (Fragment f : allFragments) {
            if (f != null && f != targetFragment && f.isAdded()) {
                ft.hide(f);
            }
        }

        if (!targetFragment.isAdded()) {
            String tag = getFragmentTag(targetFragment);
            ft.add(R.id.fragment_container, targetFragment, tag);
        } else {
            ft.show(targetFragment);
        }

        ft.commitAllowingStateLoss();
        activeFragment = targetFragment;
    }

    private String getFragmentTag(Fragment fragment) {
        if (fragment instanceof HomeFragment) return "1";
        if (fragment instanceof SearchFragment) return "2";
        if (fragment instanceof LibraryFragment) return "3";
        if (fragment instanceof SettingsFragment) return "4";
        return null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNavigation != null) {
            outState.putInt(KEY_SELECTED_NAV, bottomNavigation.getSelectedItemId());
        }
    }
}
