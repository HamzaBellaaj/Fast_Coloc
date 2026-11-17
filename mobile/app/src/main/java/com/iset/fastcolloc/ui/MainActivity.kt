package com.iset.fastcolloc.ui
import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.iset.fastcolloc.ui.auth.AuthActivity
class MainActivity : AppCompatActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); startActivity(Intent(this, AuthActivity::class.java)); finish() }
}
