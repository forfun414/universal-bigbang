package com.universal.textboom.floating;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.universal.textboom.R;

import static android.R.id.list;

public class CustomAdapter extends ArrayAdapter{

    private int resource;
    private LayoutInflater inflater;
    private Context context;

    public CustomAdapter ( Context ctx, int resourceId, List list) {
        super( ctx, resourceId, list );
        resource = resourceId;
        inflater = LayoutInflater.from( ctx );
        context = ctx;
    }

    @Override
    public View getView (int position, View convertView, ViewGroup parent) {
        convertView = (LinearLayout) inflater.inflate(resource, null);

        TextView txtName = (TextView) convertView.findViewById(R.id.textView1);
        txtName.setText("Bang" + position);

        ImageView imageCity = (ImageView) convertView.findViewById(R.id.imageView1);
        imageCity.setImageResource(R.drawable.icon_bigbang);
        return convertView;
    }
}