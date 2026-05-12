package com.example.patientapp;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import com.example.patientapp.utils.ToastHelper;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private Fragment homeFragment, historyFragment, profileFragment;
    private Fragment activeFragment;
    private final FragmentManager fm = getSupportFragmentManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);
        
        setupFragments();
        setupBottomNavigation();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0); // Bottom padding handled by BottomNav
            return insets;
        });
    }

    private void setupFragments() {
        homeFragment = new HomeFragment();
        historyFragment = new HistoryFragment();
        profileFragment = new ProfileFragment();

        activeFragment = homeFragment;

        fm.beginTransaction().add(R.id.fragment_container, profileFragment, "3").hide(profileFragment).commit();
        fm.beginTransaction().add(R.id.fragment_container, historyFragment, "2").hide(historyFragment).commit();
        fm.beginTransaction().add(R.id.fragment_container, homeFragment, "1").commit();
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                fm.beginTransaction().hide(activeFragment).show(homeFragment).commit();
                activeFragment = homeFragment;
                return true;
            } else if (id == R.id.nav_history) {
                fm.beginTransaction().hide(activeFragment).show(historyFragment).commit();
                activeFragment = historyFragment;
                return true;
            } else if (id == R.id.nav_profile) {
                fm.beginTransaction().hide(activeFragment).show(profileFragment).commit();
                activeFragment = profileFragment;
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        ToastHelper.clear();
        super.onDestroy();
    }
}