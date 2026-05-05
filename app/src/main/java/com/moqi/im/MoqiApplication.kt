package com.moqi.im

import android.app.Application
import com.moqi.im.dict.DictionaryManager

class MoqiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DictionaryManager.init(this)
    }
}