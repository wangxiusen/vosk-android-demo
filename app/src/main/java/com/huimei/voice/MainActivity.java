package com.huimei.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DemoDestination[] destinations = DemoDestination.values();
        ListView demoList = findViewById(R.id.demo_list);
        demoList.setAdapter(new DemoAdapter(this, destinations));
        demoList.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, destinations[position].targetActivity())));
    }

    private static final class DemoAdapter extends ArrayAdapter<DemoDestination> {
        private final LayoutInflater inflater;

        DemoAdapter(Context context, DemoDestination[] destinations) {
            super(context, android.R.layout.simple_list_item_2, destinations);
            inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
                row.setMinimumHeight(getContext().getResources().getDimensionPixelSize(
                        R.dimen.demo_list_item_min_height));
            }

            DemoDestination destination = getItem(position);
            if (destination != null) {
                TextView title = row.findViewById(android.R.id.text1);
                TextView summary = row.findViewById(android.R.id.text2);
                title.setText(destination.titleResourceId());
                title.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                summary.setText(destination.summaryResourceId());
                summary.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
            }
            return row;
        }
    }
}
