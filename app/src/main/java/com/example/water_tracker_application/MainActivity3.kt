package com.example.water_tracker_application

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity3 : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)

        val adapter = MyFragmentAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Set the text for each tab here.
            when (position) {
                0 -> tab.text = "Home"
                1 -> tab.text = "History"
            }
        }.attach()
    }

    private inner class MyFragmentAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int {
            return 2 // Number of tabs
        }

        override fun createFragment(position: Int): Fragment {
            // Return the appropriate fragment for each tab
            return when (position) {
                0 -> HomeFragment()
                1 -> HistoryFragment()
                else -> HomeFragment() // Default fragment
            }
        }
    }

    override fun onBackPressed() {
        finish()
        moveTaskToBack(true)
    }

}