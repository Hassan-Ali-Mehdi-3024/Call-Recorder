package com.callrecorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {
    
    private List<File> recordings;
    
    public RecordingsAdapter(List<File> recordings) {
        this.recordings = recordings;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = recordings.get(position);
        holder.textFileName.setText(file.getName());
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateStr = dateFormat.format(new Date(file.lastModified()));
        long fileSizeKB = file.length() / 1024;
        
        holder.textFileInfo.setText(dateStr + " • " + fileSizeKB + " KB");
    }
    
    @Override
    public int getItemCount() {
        return recordings.size();
    }
    
    public void updateRecordings(List<File> newRecordings) {
        this.recordings = newRecordings;
        notifyDataSetChanged();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textFileName;
        TextView textFileInfo;
        
        ViewHolder(View itemView) {
            super(itemView);
            textFileName = itemView.findViewById(android.R.id.text1);
            textFileInfo = itemView.findViewById(android.R.id.text2);
        }
    }
}
