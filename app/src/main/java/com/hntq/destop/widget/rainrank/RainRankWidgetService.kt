package com.hntq.destop.widget.rainrank

import android.content.Intent
import android.widget.RemoteViewsService

class RainRankWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RainRankWidgetViewsFactory(applicationContext, intent)
    }
}

