package com.example.gptbackup.adapter;

import com.bumptech.glide.Glide;
import java.io.File;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gptbackup.R;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final List<FileModel> fileList;
    private final List<FileModel> selectedFiles = new ArrayList<>();
    private OnFileClickListener onFileClickListener;

    public interface OnFileClickListener {
        void onFileClicked(FileModel file);
        void onSelectionChanged(int selectedCount);
    }

    public FileAdapter(List<FileModel> fileList) {
        this.fileList = fileList;
    }

    public void setOnFileClickListener(OnFileClickListener listener) {
        this.onFileClickListener = listener;
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
        File realFile = new File(file.getPath());

        holder.txtName.setText(file.getName());
        holder.txtMeta.setText(readableFileSize(file.getSize()));

        /* ================= PREVIEW ================= */

        // Clear previous image to prevent stale thumbnails on recycled views
        holder.imgPreview.setImageDrawable(null);

        if (file.isDirectory()) {
            holder.imgPreview.setImageResource(R.drawable.ic_folder);
        } else {
            String ext = file.getExtension().toLowerCase();
            if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp")) {
                Glide.with(holder.itemView.getContext()).load(realFile).centerCrop().override(100, 100).placeholder(R.drawable.ic_image).into(holder.imgPreview);
            } else if (ext.equals("mp4") || ext.equals("mkv")) {
                Glide.with(holder.itemView.getContext()).load(realFile).frame(1_000_000).centerCrop().override(100, 100).placeholder(R.drawable.ic_video).into(holder.imgPreview);
            } else if (ext.equals("mp3") || ext.equals("wav")) {
                holder.imgPreview.setImageResource(R.drawable.ic_audio);
            } else if (ext.equals("pdf")) {
                holder.imgPreview.setImageResource(R.drawable.ic_document);
            } else {
                holder.imgPreview.setImageResource(R.drawable.ic_file);
            }
        }

        /* ================= PRIORITY ================= */
        String priorityLabel = file.getPriority() >= 70 ? "High" : (file.getPriority() >= 40 ? "Medium" : "Low");
        holder.chipPriority.setText(priorityLabel);

        /* ================= UPLOAD STATE & ROW ACTIONS ================= */
        UploadState state = file.getUploadState();
        holder.txtUploadState.setText(state.name());

        boolean isBusy = (state == UploadState.UPLOADING || state == UploadState.PAUSED);

        if (isBusy) {
            holder.layoutFileProgress.setVisibility(View.VISIBLE);
            holder.progressFile.setProgress(file.getUploadProgress());
            holder.txtFileProgressPercent.setText(file.getUploadProgress() + "%");
            holder.chkSelect.setVisibility(View.INVISIBLE); // Hide checkbox during active upload

            if (state == UploadState.PAUSED) {
                holder.btnRowPause.setVisibility(View.GONE);
                holder.btnRowResume.setVisibility(View.VISIBLE);
            } else {
                holder.btnRowPause.setVisibility(View.VISIBLE);
                holder.btnRowResume.setVisibility(View.GONE);
            }
        } else {
            holder.layoutFileProgress.setVisibility(View.GONE);
            if (file.isSelected()) {
                holder.chkSelect.setVisibility(View.VISIBLE);
            } else {
                holder.chkSelect.setVisibility(View.GONE);
            }
        }

        /* ================= SELECTION (LONG PRESS) ================= */
        holder.chkSelect.setOnCheckedChangeListener(null);
        holder.chkSelect.setChecked(selectedFiles.contains(file));
        holder.chkSelect.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) { if (!selectedFiles.contains(file)) selectedFiles.add(file); }
            else { 
                selectedFiles.remove(file); 
                holder.chkSelect.setVisibility(View.GONE);
            }
            if (onFileClickListener != null) onFileClickListener.onSelectionChanged(selectedFiles.size());
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isBusy) {
                holder.chkSelect.setVisibility(View.VISIBLE);
                holder.chkSelect.setChecked(true);
                if (!selectedFiles.contains(file)) selectedFiles.add(file);
                if (onFileClickListener != null) onFileClickListener.onSelectionChanged(selectedFiles.size());
            }
            return true;
        });

        /* ================= ROW BUTTON LISTENERS ================= */
        holder.btnRowPause.setOnClickListener(v -> {
            file.pauseByUser();
            notifyItemChanged(position);
        });

        holder.btnRowResume.setOnClickListener(v -> {
            file.resumeByUser();
            notifyItemChanged(position);
        });

        holder.btnRowCancel.setOnClickListener(v -> {
            file.cancelByUser();
            selectedFiles.remove(file);
            notifyItemChanged(position);
        });

        /* ================= ITEM CLICK ================= */
        holder.itemView.setOnClickListener(v -> {
            if (file.isDirectory()) { 
                if (onFileClickListener != null) onFileClickListener.onFileClicked(file); 
            } else if (file.isSelected() && !isBusy) {
                file.setSelected(false);
                selectedFiles.remove(file);
                notifyItemChanged(position);
                if (onFileClickListener != null) onFileClickListener.onSelectionChanged(selectedFiles.size());
            } else if (!isBusy) {
                openExternally(v, file);
            }
        });
    }

    @Override
    public int getItemCount() { return fileList.size(); }

    public List<FileModel> getSelectedFiles() { return new ArrayList<>(selectedFiles); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtMeta, txtUploadState, txtFileProgressPercent;
        CheckBox chkSelect;
        ImageView imgPreview;
        Chip chipPriority;
        ProgressBar progressFile;
        View layoutFileProgress, btnRowPause, btnRowResume, btnRowCancel;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtFileName);
            txtMeta = itemView.findViewById(R.id.txtMeta);
            txtUploadState = itemView.findViewById(R.id.txtUploadState);
            chkSelect = itemView.findViewById(R.id.chkSelect);
            imgPreview = itemView.findViewById(R.id.imgPreview);
            chipPriority = itemView.findViewById(R.id.chipPriority);
            progressFile = itemView.findViewById(R.id.progressFile);
            layoutFileProgress = itemView.findViewById(R.id.layoutFileProgress);
            txtFileProgressPercent = itemView.findViewById(R.id.txtFileProgressPercent);
            btnRowPause = itemView.findViewById(R.id.btnRowPause);
            btnRowResume = itemView.findViewById(R.id.btnRowResume);
            btnRowCancel = itemView.findViewById(R.id.btnRowCancel);
        }
    }

    private void openExternally(View view, FileModel file) {
        try {
            File realFile = new File(file.getPath());
            Uri uri = FileProvider.getUriForFile(view.getContext(), view.getContext().getPackageName() + ".provider", realFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType(file.getPath()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.getContext().startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(view.getContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String path) {
        String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(path);
        return ext != null ? android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase()) : "*/*";
    }

    private String readableFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
