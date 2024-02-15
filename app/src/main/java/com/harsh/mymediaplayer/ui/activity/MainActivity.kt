package com.harsh.mymediaplayer.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.harsh.mymediaplayer.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}