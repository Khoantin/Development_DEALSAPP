package com.example.androidapp.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class FirebaseStorageTest {

    @Test
    fun testFirebaseStorageRepositoryClassReference() {
        assertNotNull(FirebaseStorageRepository::class.java)
    }
}
