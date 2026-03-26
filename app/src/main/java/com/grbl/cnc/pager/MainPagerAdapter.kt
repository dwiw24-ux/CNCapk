package com.grbl.cnc.pager

import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.fragment.app.Fragment


class MainPagerAdapter(activity: AppCompatActivity)
    : FragmentStateAdapter(activity) {

    val consoleFragment = ConsoleFragment()

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> JogFragment()
            1 -> FileFragment()
            2 -> GcodeFragment()
            3 -> ConsoleFragment()
            else -> JogFragment()
        }
    }
}
