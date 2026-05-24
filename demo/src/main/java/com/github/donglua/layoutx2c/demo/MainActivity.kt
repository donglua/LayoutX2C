package com.github.donglua.layoutx2c.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.donglua.layoutx2c.runtime.LayoutX2CRegistry

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 优先用 generated factory，fallback 到 LayoutInflater
        val rootView = LayoutX2CRegistry.inflate(
            context = this,
            layoutId = R.layout.activity_main,
            parent = null
        )
        setContentView(rootView)

        findViewById<TextView>(R.id.title)?.setOnClickListener {
            startActivity(Intent(this, BenchmarkActivity::class.java))
        }
    }
}
