package com.grbl.cnc.ui.pager

import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.fragment.app.Fragment


class MainPagerAdapter(activity: AppCompatActivity)
    : FragmentStateAdapter(activity) {

    val consoleFragment = ConsoleFragment()

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> JogFragment()
            1 -> ProbeFragment()
            2 -> FileFragment()
            3 -> EditorFragment()
            else -> ConsoleFragment()
        }
    }
}
