package com.tqfapp.android

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDatabaseTest {

    @Test
    fun testUserCrud() = runBlocking {

        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        val userDao = database.userDao()

        val user = User(
            id = "1",
            name = "Teste",
            email = "teste@tqf.com",
            isProvider = false
        )

        // CREATE
        userDao.insert(user)

        // READ
        val savedUser = userDao.getById("1")
        assertEquals("Teste", savedUser?.name)

        // UPDATE
        userDao.update(
            user.copy(name = "Teste Atualizado")
        )

        val updatedUser = userDao.getById("1")
        assertEquals("Teste Atualizado", updatedUser?.name)

        // DELETE
        userDao.delete(updatedUser!!)

        val deletedUser = userDao.getById("1")
        assertNull(deletedUser)

        database.close()
    }
}