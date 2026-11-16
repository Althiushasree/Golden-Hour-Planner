package com.example

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.auth.AuthManager
import com.example.ui.DashboardActivity
import com.example.ui.LoginActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val destination = if (AuthManager().currentUser() != null) {
            DashboardActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }
}
