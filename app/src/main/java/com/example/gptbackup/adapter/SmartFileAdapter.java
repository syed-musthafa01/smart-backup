package com.example.gptbackup.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.gptbackup.R;
import com.example.gptbackup.model.FileModel;

import java.io.File;
import java.util.List;

public class SmartFileAdapter
        extends RecyclerView.Adapter<SmartFileAdapter.ViewHolder> {

    private final List<FileModel> files;

    public SmartFileAdapter(List<FileModel> files) {
        this.files = files;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_smart_file, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int position) {

        FileModel file = files.get(position);
        Context context = holder.itemView.getContext();

        holder.txtFileName.setText(file.getName());
        holder.txtPriority.setText(getPriorityText(file.getPriority()));

        File realFile = new File(file.getPath());

        // 🔹 Preview or icon
        if (realFile.exists() && !file.isDirectory()
                && (file.isImage() || file.isVideo())) {

            Glide.with(context)
                    .load(realFile)
                    .centerCrop()
                    .placeholder(R.drawable.ic_file)
                    .into(holder.imgFileIcon);
        } else {
            holder.imgFileIcon.setImageResource(getFileIcon(file));
        }

        // 🔹 Checkbox (default checked)
        holder.checkSelect.setOnCheckedChangeListener(null);
        holder.checkSelect.setChecked(file.isSelected());
        holder.checkSelect.setOnCheckedChangeListener(
                (buttonView, isChecked) -> file.setSelected(isChecked)
        );

        // 🔹 OPEN FILE ON CLICK
        holder.itemView.setOnClickListener(v ->
                openFileExternally(context, realFile)
        );
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // ---------------- FILE OPEN LOGIC ----------------

    private void openFileExternally(Context context, File file) {

        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            String mimeType = getMimeType(file.getName());
            if (mimeType == null) mimeType = "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(intent, "Open with"));

        } catch (Exception e) {
            Toast.makeText(context,
                    "No app found to open this file",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileName) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (ext == null || ext.isEmpty()) return null;
        return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext.toLowerCase());
    }

    // ---------------- HELPERS ----------------

    private String getPriorityText(int priority) {
        if (priority >= 70) return "HIGH";
        if (priority >= 40) return "MEDIUM";
        return "LOW";
    }

    private int getFileIcon(FileModel file) {
        if (file.isDirectory()) return R.drawable.ic_folder;
        if (file.isAudio()) return R.drawable.ic_audio;
        if (file.isVideo()) return R.drawable.ic_video;
        if (file.isDocument()) return R.drawable.ic_document;
        return R.drawable.ic_file;
    }

    // ---------------- VIEW HOLDER ----------------

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgFileIcon;
        TextView txtFileName, txtPriority;
        CheckBox checkSelect;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFileIcon = itemView.findViewById(R.id.imgFileIcon);
            txtFileName = itemView.findViewById(R.id.txtFileName);
            txtPriority = itemView.findViewById(R.id.txtPriority);
            checkSelect = itemView.findViewById(R.id.checkSelect);
        }
    }
}
