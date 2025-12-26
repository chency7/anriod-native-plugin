package com.hntq.destop.widget

import com.google.gson.annotations.SerializedName

data class HitokotoResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("hitokoto") val hitokoto: String,
    @SerializedName("from") val from: String,
    @SerializedName("from_who") val fromWho: String?,
    @SerializedName("creator") val creator: String,
    @SerializedName("created_at") val createdAt: String
)
