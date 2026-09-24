package com.dnfapps.arrmatey.backup.model

import com.dnfapps.arrmatey.instances.model.InstanceHeader
import kotlinx.serialization.Serializable

@Serializable
data class CustomWebpageExport(
    val id: Long = 0,
    val name: String,
    val url: String,
    val headers: List<InstanceHeader> = emptyList(),
)
