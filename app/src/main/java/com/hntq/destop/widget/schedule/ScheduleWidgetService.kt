package com.hntq.destop.widget.schedule

import android.content.Intent
import android.widget.RemoteViewsService

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetViewsFactory(applicationContext, intent)
    }
}

