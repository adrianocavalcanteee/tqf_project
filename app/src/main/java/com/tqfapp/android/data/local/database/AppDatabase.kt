package com.tqfapp.android.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tqfapp.android.data.local.dao.ProposalDao
import com.tqfapp.android.data.local.dao.ReviewDao
import com.tqfapp.android.data.local.dao.ServiceDao
import com.tqfapp.android.data.local.dao.UserDao
import com.tqfapp.android.data.model.Proposal
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.User

@Database(
    entities = [User::class, Service::class, Proposal::class, Review::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun serviceDao(): ServiceDao
    abstract fun proposalDao(): ProposalDao
    abstract fun reviewDao(): ReviewDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tqf_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}