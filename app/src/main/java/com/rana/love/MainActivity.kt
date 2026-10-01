package com.rana.love

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val actions = mapOf(
            R.id.memories to "قسم ذكرياتنا — سنضيف أول ذكرى بتاريخ 5/3/2026.",
            R.id.photos to "قسم الصور جاهز لإضافة صوركم لاحقًا.",
            R.id.poems to "قسم الشعر — سنضع فيه قصائد ورسائل خاصة لرنا.",
            R.id.chat to "قسم المحادثة — الواجهة ستتصل لاحقًا بخادم للمراسلة بين الجهازين.",
            R.id.music to "أغانينا: بينا ميعاد."
        )

        actions.forEach { (id, message) ->
            findViewById<Button>(id).setOnClickListener {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
