package com.example.gptbackup.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
    private int lastPosition = -1;
    private SelectionListener selectionListener;

    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    public SmartFileAdapter(List<FileModel> files) {
        this.files = files;
        setHasStableIds(true);
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

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

        setAnimation(holder.itemView, position);

        File realFile = (file.getPath() != null && !file.getPath().isEmpty()) ? new File(file.getPath()) : null;

        holder.txtName.setText(file.getName());
        
        String typeStr = file.getType() == null ? "OTHER" : file.getType().toUpperCase(Locale.US);
        holder.txtInfo.setText(typeStr + " • " + formatSize(file.getSize()) + " • " + formatDate(file.getLastModified()));

        int p = file.getPriority();
        String pLabel;
        int pColor;
        if (p >= 70) {
            pLabel = "HIGH";
            pColor = Color.parseColor("#D50000"); 
        } else if (p >= 40) {
            pLabel = "MEDIUM";
            pColor = Color.parseColor("#FFAB00"); 
        } else {
            pLabel = "LOW";
            pColor = Color.parseColor("#00C853"); 
        }
        holder.txtPriorityTag.setText(pLabel);
        holder.txtPriorityTag.getBackground().setTint(pColor);

        UploadState state = file.getUploadState();
        holder.txtStateTag.setText(state != null ? state.name() : "IDLE");

        // Clear previous image to prevent stale thumbnails on recycled views
        holder.imgPreview.setImageDrawable(null);

        if (!file.isDirectory() && (file.isImage() || file.isVideo())) {
            Glide.with(context)
                    .load(file.isFromMediaStore() ? file.getContentUri() : realFile)
                    .centerCrop()
                    .placeholder(R.drawable.ic_file_generic)
                    .into(holder.imgPreview);
        } else {
            holder.imgPreview.setImageResource(getFileIcon(file));
        }

        holder.checkSelect.setOnCheckedChangeListener(null);
        holder.checkSelect.setChecked(file.isSelected());
        holder.checkSelect.setOnCheckedChangeListener((btn, checked) -> {
            file.setSelected(checked);
            notifySelectionChanged();
        });

        boolean isBusy = backupRunning && file.isSelected() && (state == UploadState.UPLOADING || state == UploadState.PAUSED);
        
        holder.itemView.setOnLongClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return true;
            FileModel clickedFile = files.get(currentPos);
            if (!isBusy) {
                clickedFile.setSelected(true);
                notifyItemChanged(currentPos);
                notifySelectionChanged();
            }
            return true;
        });

        if (file.isSelected()) {
            holder.checkSelect.setVisibility(isBusy ? View.INVISIBLE : View.VISIBLE);
        } else {
            holder.checkSelect.setVisibility(View.GONE);
        }

        if (isBusy) {
            holder.progressContainer.setVisibility(View.VISIBLE);
            holder.fileProgressBar.setProgress(file.getUploadProgress());
            holder.txtProgressPercent.setText(file.getUploadProgress() + "%");

            if (state == UploadState.PAUSED) {
                holder.btnRowPause.setVisibility(View.GONE);
                holder.btnRowResume.setVisibility(View.VISIBLE);
            } else {
                holder.btnRowPause.setVisibility(View.VISIBLE);
                holder.btnRowResume.setVisibility(View.GONE);
            }
            holder.btnRowCancel.setVisibility(View.VISIBLE);
        } else {
            holder.progressContainer.setVisibility(View.GONE);
        }

        holder.btnRowPause.setOnClickListener(v -> { 
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                files.get(pos).pauseByUser(); 
                notifyItemChanged(pos); 
            }
        });
        holder.btnRowResume.setOnClickListener(v -> { 
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                files.get(pos).resumeByUser(); 
                notifyItemChanged(pos); 
            }
        });
        holder.btnRowCancel.setOnClickListener(v -> { 
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                FileModel f = files.get(pos);
                f.cancelByUser(); 
                f.setSelected(false);
                notifyItemChanged(pos); 
                notifySelectionChanged();
            }
        });

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            FileModel clickedFile = files.get(currentPos);
            
            if (clickedFile.isSelected() && !isBusy) {
                clickedFile.setSelected(false);
                notifyItemChanged(currentPos);
                notifySelectionChanged();
            } else if (!isBusy) {
                if (getSelectedCount() > 0) {
                    clickedFile.setSelected(true);
                    notifyItemChanged(currentPos);
                    notifySelectionChanged();
                } else {
                    openFile(context, clickedFile);
                }
            }
        });
    }

    private int getSelectedCount() {
        int count = 0;
        for (FileModel f : files) {
            if (f.isSelected()) count++;
        }
        return count;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedCount());
        }
    }

    public void selectAll(boolean select) {
        for (FileModel f : files) {
            f.setSelected(select);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void openFile(Context context, FileModel file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = getMimeType(file.getName());
            if (mime == null) mime = "*/*";
            Uri uri = file.isFromMediaStore() ? file.getContentUri() : 
                      FileProvider.getUriForFile(context, context.getPackageName() + ".provider", new File(file.getPath()));
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show();
        }
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(viewToAnimate.getContext(), R.anim.item_animation_fall_down);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    @Override
    public int getItemCount() { return files.size(); }

    @Override
    public long getItemId(int position) {
        FileModel f = files.get(position);
        if (f.getMediaStoreId() > 0) return f.getMediaStoreId();
        String path = f.getPath();
        return path != null ? path.hashCode() : position;
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
        return new SimpleDateFormat("dd/MM/yy", Locale.US).format(new Date(millis));
    }

    private String getMimeType(String fileName) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
        return ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
    }

    private int getFileIcon(FileModel file) {
        if (file.isDirectory()) return R.drawable.ic_folder;
        if (file.isAudio()) return R.drawable.ic_audio;
        if (file.isVideo()) return R.drawable.ic_video;
        if (file.isDocument()) return R.drawable.ic_document;
        return R.drawable.ic_file_generic;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPreview;
        TextView txtName, txtInfo, txtPriorityTag, txtStateTag, txtProgressPercent;
        CheckBox checkSelect;
        View progressContainer, btnRowPause, btnRowResume, btnRowCancel;
        ProgressBar fileProgressBar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPreview = itemView.findViewById(R.id.imgPreview);
            txtName = itemView.findViewById(R.id.txtName);
            txtInfo = itemView.findViewById(R.id.txtInfo);
            txtPriorityTag = itemView.findViewById(R.id.txtPriorityTag);
            txtStateTag = itemView.findViewById(R.id.txtStateTag);
            txtProgressPercent = itemView.findViewById(R.id.txtProgressPercent);
            checkSelect = itemView.findViewById(R.id.checkSelect);
            progressContainer = itemView.findViewById(R.id.progressContainer);
            fileProgressBar = itemView.findViewById(R.id.fileProgressBar);
            btnRowPause = itemView.findViewById(R.id.btnRowPause);
            btnRowResume = itemView.findViewById(R.id.btnRowResume);
            btnRowCancel = itemView.findViewById(R.id.btnRowCancel);
        }
    }
}
