package com.example.streetsafe_code

data class Report(

    val incidentType: String = "",
    val location: String = "",
    val riskLevel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = ""
)