package com.huimei.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val destinations = DemoDestination.values()
        val demoList = findViewById<ListView>(R.id.demo_list)
        demoList.setAdapter(DemoAdapter(this, destinations))
        demoList.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, destinations[position].targetActivity()))
        }
    }

    private class DemoAdapter(context: Context, destinations: Array<DemoDestination>) :
        ArrayAdapter<DemoDestination>(context, android.R.layout.simple_list_item_2, destinations) {
        private val inflater: LayoutInflater

        init {
            inflater = LayoutInflater.from(context)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var row = convertView
            if (row == null) {
                row = inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                row.setMinimumHeight(
                    getContext().getResources().getDimensionPixelSize(
                        R.dimen.demo_list_item_min_height
                    )
                )
            }

            val destination = getItem(position)
            if (destination != null) {
                val title = row.findViewById<TextView>(android.R.id.text1)
                val summary = row.findViewById<TextView>(android.R.id.text2)
                title.setText(destination.titleResourceId())
                title.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary))
                summary.setText(destination.summaryResourceId())
                summary.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary))
            }
            return row
        }
    }
}
