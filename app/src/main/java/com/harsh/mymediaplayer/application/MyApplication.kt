package com.harsh.mymediaplayer.application

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Singleton

@HiltAndroidApp
@Singleton
class MyApplication: Application() {
}