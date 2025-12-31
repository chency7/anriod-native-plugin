package com.hntq.destop.widget.hotsearch

import com.google.gson.annotations.SerializedName

data class HotSearchResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: List<HotSearchItem>
)

data class HotSearchItem(
    @SerializedName("hot") val hot: String?,
    @SerializedName("index") val index: Int,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String
)

