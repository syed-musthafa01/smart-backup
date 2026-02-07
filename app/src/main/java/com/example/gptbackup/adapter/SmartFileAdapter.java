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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.gptbackup.R;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmartFileAdapter
        extends RecyclerView.Adapter<SmartFileAdapter.ViewHolder> {

    private final List<FileModel> files;
    private boolean backupRunning = false;

    public SmartFileAdapter(List<FileModel> files) {
        this.files = files;
    }

    // 🔹 Called from Activity
    public void setBackupRunning(boolean running) {
        this.backupRunning = running;
        notifyDataSetChanged();
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
        File realFile = new File(file.getPath());

        // ================= FILE NAME =================
        holder.txtName.setText(file.getName());

        // ================= FILE INFO =================
        holder.txtInfo.setText(buildInfoText(file));

        // ================= PREVIEW =================
        if (realFile.exists() && !file.isDirectory()
                && (file.isImage() || file.isVideo())) {

            Glide.with(context)
                    .load(realFile)
                    .centerCrop()
                    .placeholder(R.drawable.ic_file_generic)
                    .into(holder.imgPreview);
        } else {
            holder.imgPreview.setImageResource(getFileIcon(file));
        }

        // ================= CHECKBOX =================
        holder.checkSelect.setOnCheckedChangeListener(null);
        holder.checkSelect.setChecked(file.isSelected());
        holder.checkSelect.setOnCheckedChangeListener(
                (buttonView, isChecked) -> file.setSelected(isChecked)
        );

        // ================= UPLOAD CONTROLS =================

        // Show controls ONLY when backup is running
        holder.uploadControls.setVisibility(
                backupRunning ? View.VISIBLE : View.GONE
        );

        // Reset all first (VERY IMPORTANT)
        holder.btnPause.setVisibility(View.GONE);
        holder.btnResume.setVisibility(View.GONE);
        holder.btnCancel.setVisibility(View.GONE);

        if (backupRunning) {
            UploadState state = file.getUploadState();

            if (state == UploadState.UPLOADING) {
                holder.btnPause.setVisibility(View.VISIBLE);
                holder.btnCancel.setVisibility(View.VISIBLE);
            }
            else if (state == UploadState.PAUSED) {
                holder.btnResume.setVisibility(View.VISIBLE);
                holder.btnCancel.setVisibility(View.VISIBLE);
            }
        }

        // ================= BUTTON ACTIONS =================
        holder.btnPause.setOnClickListener(v -> {
            file.pauseByUser();
            notifyItemChanged(position);
        });

        holder.btnResume.setOnClickListener(v -> {
            file.resumeByUser();
            notifyItemChanged(position);
        });

        holder.btnCancel.setOnClickListener(v -> {
            file.cancelByUser();
            notifyItemChanged(position);
        });

        // ================= OPEN FILE =================
        holder.itemView.setOnClickListener(v ->
                openFileExternally(context, realFile)
        );
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    // ================= INFO TEXT =================

    private String buildInfoText(FileModel file) {

        String type = file.getType() == null
                ? "OTHER"
                : file.getType().toUpperCase(Locale.US);

        String size = formatSize(file.getSize());
        String priority = getPriorityLabel(file.getPriority());
        String date = formatDate(file.getLastModified());
        String state = getUploadStateLabel(file.getUploadState());

        return type + " • " + size + " • " + priority + " • " + date + " • " + state;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        float kb = bytes / 1024f;
        float mb = kb / 1024f;

        if (mb >= 1) return String.format(Locale.US, "%.1f MB", mb);
        if (kb >= 1) return String.format(Locale.US, "%.1f KB", kb);
        return bytes + " B";
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("dd/MM/yy", Locale.US)
                .format(new Date(millis));
    }

    private String getPriorityLabel(int priority) {
        if (priority >= 70) return "HIGH";
        if (priority >= 40) return "MEDIUM";
        return "LOW";
    }

    private String getUploadStateLabel(UploadState state) {
        if (state == null) return "PENDING";
        switch (state) {
            case UPLOADING: return "UPLOADING";
            case PAUSED: return "PAUSED";
            case COMPLETED: return "COMPLETED";
            case FAILED: return "FAILED";
            case SKIPPED: return "SKIPPED";
            case CANCELED: return "CANCELED";
            default: return "PENDING";
        }
    }

    // ================= FILE OPEN =================

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

    // ================= ICON =================

    private int getFileIcon(FileModel file) {
        if (file.isDirectory()) return R.drawable.ic_folder;
        if (file.isAudio()) return R.drawable.ic_audio;
        if (file.isVideo()) return R.drawable.ic_video;
        if (file.isDocument()) return R.drawable.ic_document;
        return R.drawable.ic_file_generic;
    }

    // ================= VIEW HOLDER =================

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgPreview;
        ImageView btnPause, btnResume, btnCancel;
        TextView txtName, txtInfo;
        CheckBox checkSelect;
        LinearLayout uploadControls;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            imgPreview = itemView.findViewById(R.id.imgPreview);
            txtName = itemView.findViewById(R.id.txtName);
            txtInfo = itemView.findViewById(R.id.txtInfo);
            checkSelect = itemView.findViewById(R.id.checkSelect);

            btnPause = itemView.findViewById(R.id.btnPause);
            btnResume = itemView.findViewById(R.id.btnResume);
            btnCancel = itemView.findViewById(R.id.btnCancel);

            uploadControls = itemView.findViewById(R.id.upl);
        }
    }
}
