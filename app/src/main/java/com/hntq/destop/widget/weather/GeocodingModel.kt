package com.hntq.destop.widget.weather

import com.google.gson.annotations.SerializedName

data class GeocodingResponse(
    val status: String?,
    val info: String?,
    val regeocode: Regeocode?
)

data class Regeocode(
    @SerializedName("formatted_address")
    val formattedAddress: String?,
    @SerializedName("addressComponent")
    val addressComponent: AddressComponent?
)

data class AddressComponent(
    val country: String?,
    val province: String?,
    // 高德在直辖市时 city 可能返回空数组 []，使用 Any? 避免解析错误
    val city: Any?, 
    val district: String?,
    val township: String?
)