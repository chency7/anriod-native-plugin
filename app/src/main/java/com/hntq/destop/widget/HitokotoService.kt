package com.hntq.destop.widget

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface HitokotoService {
    @GET("/")
    fun getHitokoto(@Query("c") category: String? = null): Call<HitokotoResponse>
}
