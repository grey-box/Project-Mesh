package com.greybox.projectmesh.testing


import android.util.Log
import com.ustadmobile.meshrabiya.log.MNetLogger

class TestMNetLogger : MNetLogger() {
    override fun invoke(priority: Int, message: String, exception: Exception?) {
        Log.println(priority, "TestDevice", message)
        exception?.let {
            Log.println(priority, "TestDevice", it.toString())
        }
    }

    override fun invoke(priority: Int, message: () -> String, exception: Exception?) {
        Log.println(priority, "TestDevice", message())
        exception?.let {
            Log.println(priority, "TestDevice", it.toString())
        }
    }
}