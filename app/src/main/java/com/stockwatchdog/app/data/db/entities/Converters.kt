package com.stockwatchdog.app.data.db.entities

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun alertTypeToString(t: AlertType): String = t.name
    @TypeConverter fun alertTypeFromString(s: String): AlertType = AlertType.valueOf(s)
}
