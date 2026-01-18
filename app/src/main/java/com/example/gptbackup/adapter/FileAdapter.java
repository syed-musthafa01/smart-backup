package com.example.gptbackup.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.model.FileModel;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final List<FileModel> fileList;

    public FileAdapter(List<FileModel> fileList) {
        this.fileList = fileList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileModel file = fileList.get(position);

        holder.txtName.setText(file.getName());
        holder.txtType.setText("Type: " + file.getType());

        String p = "Priority: -";
        if (file.getPriority() == 2) p = "Priority: HIGH";
        else if (file.getPriority() == 1) p = "Priority: MEDIUM";
        else if (file.getPriority() == 0) p = "Priority: LOW";

        holder.txtPriority.setText(p);
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtName, txtType, txtPriority;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtFileName);
            txtType = itemView.findViewById(R.id.txtFileType);
            txtPriority = itemView.findViewById(R.id.txtPriority);
        }
    }
}
