package com.musicplayer.app.widget

import androidx.media3.common.util.UnstableApi
import com.musicplayer.app.R

@UnstableApi
class MusicWidgetLargeProvider : MusicWidgetProvider() {
    override fun getLayoutId(): Int = R.layout.widget_large
}
