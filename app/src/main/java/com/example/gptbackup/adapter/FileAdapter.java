package com.example.gptbackup.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.model.FileModel;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final List<FileModel> fileList;
    private final List<FileModel> selectedFiles = new ArrayList<>();

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

        String priority;
        if (file.getPriority() == 2) priority = "Priority: HIGH";
        else if (file.getPriority() == 1) priority = "Priority: MEDIUM";
        else priority = "Priority: LOW";

        holder.txtPriority.setText(priority);

        // 🔁 Avoid checkbox recycle bug
        holder.chkSelect.setOnCheckedChangeListener(null);
        holder.chkSelect.setChecked(selectedFiles.contains(file));

        // ✅ Handle selection
        holder.chkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedFiles.contains(file)) {
                    selectedFiles.add(file);
                }
            } else {
                selectedFiles.remove(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    // ✅ THIS IS WHAT MainActivity NEEDS
    public List<FileModel> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtName, txtType, txtPriority;
        CheckBox chkSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtName = itemView.findViewById(R.id.txtFileName);
            txtType = itemView.findViewById(R.id.txtFileType);
            txtPriority = itemView.findViewById(R.id.txtPriority);
            chkSelect = itemView.findViewById(R.id.chkSelect);
        }
    }
}
