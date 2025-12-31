package com.hntq.destop.widget.hotsearch

import retrofit2.Call
import retrofit2.http.GET

interface HotSearchService {
    @GET("api/weibohot")
    fun getWeiboHot(): Call<HotSearchResponse>

    @GET("api/baiduhot")
    fun getBaiduHot(): Call<HotSearchResponse>
}

