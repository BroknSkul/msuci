package com.musicplayer.app.data.repository

import com.musicplayer.app.data.local.database.NotificationDao
import com.musicplayer.app.data.local.database.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao
) {
    val allNotifications: Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    suspend fun insertNotification(title: String, message: String, type: String = "info") {
        notificationDao.insertNotification(
            NotificationEntity(title = title, message = message, type = type)
        )
    }

    suspend fun markAsRead(id: Int) {
        notificationDao.markAsRead(id)
    }

    suspend fun deleteNotification(id: Int) {
        notificationDao.deleteNotification(id)
    }

    suspend fun clearAll() {
        notificationDao.deleteAllNotifications()
    }
}
