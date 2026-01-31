    package com.example.gptbackup.adapter;

    import com.bumptech.glide.Glide;
    import java.io.File;

    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.ImageView;
    import android.widget.CheckBox;
    import android.widget.TextView;
    import android.widget.ProgressBar;

    import androidx.annotation.NonNull;
    import androidx.recyclerview.widget.RecyclerView;

    import com.example.gptbackup.R;
    import com.example.gptbackup.model.FileModel;
    import com.example.gptbackup.model.UploadState;
    import com.google.android.material.chip.Chip;

    import java.util.ArrayList;
    import java.util.List;

    public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        private final List<FileModel> fileList;   // ✅ fixed name
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

            FileModel file = fileList.get(position); // ✅ fixed variable

            holder.txtName.setText(file.getName());
            holder.txtMeta.setText(readableFileSize(file.getSize()));

            File realFile = new File(file.getPath());

            // ================= IMAGE PREVIEW LOGIC =================

            if (file.isDirectory()) {
                holder.imgPreview.setImageResource(R.drawable.ic_folder);
            } else {

                String ext = file.getExtension().toLowerCase();

                if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp")) {

                    // ✅ Small real image thumbnail
                    Glide.with(holder.itemView.getContext())
                            .load(realFile)
                            .centerCrop()
                            .override(100, 100)   // very important for performance
                            .placeholder(R.drawable.ic_image)
                            .into(holder.imgPreview);

                } else if (ext.equals("mp4") || ext.equals("mkv")) {

                    // ✅ Video thumbnail
                    Glide.with(holder.itemView.getContext())
                            .load(realFile)
                            .frame(1_000_000) // 1 second frame
                            .centerCrop()
                            .override(100, 100)
                            .placeholder(R.drawable.ic_video)
                            .into(holder.imgPreview);

                } else if (ext.equals("mp3") || ext.equals("wav")) {

                    holder.imgPreview.setImageResource(R.drawable.ic_audio);

                } else if (ext.equals("pdf")) {

                    holder.imgPreview.setImageResource(R.drawable.ic_document);

                } else if (ext.equals("zip") || ext.equals("rar")) {

                    holder.imgPreview.setImageResource(R.drawable.ic_archive);

                } else {

                    holder.imgPreview.setImageResource(R.drawable.ic_file);
                }
            }

            // ================= PRIORITY CHIP =================

            String priorityLabel;
            if (file.getPriority() == 2) priorityLabel = "High";
            else if (file.getPriority() == 1) priorityLabel = "Medium";
            else if (file.getPriority() == 0) priorityLabel = "Low";
            else priorityLabel = "Unscored";

            holder.chipPriority.setText(priorityLabel);

            // ================= UPLOAD STATE =================

            UploadState state = file.getUploadState();
            holder.txtUploadState.setText(state.name());

            if (state == UploadState.UPLOADING) {
                holder.progressFile.setVisibility(View.VISIBLE);
                holder.progressFile.setProgress(file.getUploadProgress());
            } else if (state == UploadState.COMPLETED || state == UploadState.FAILED
                    || state == UploadState.CANCELED) {
                holder.progressFile.setVisibility(View.GONE);
            } else {
                holder.progressFile.setVisibility(View.INVISIBLE);
            }

            // ================= CHECKBOX FIX =================

            holder.chkSelect.setOnCheckedChangeListener(null);
            holder.chkSelect.setChecked(selectedFiles.contains(file));

            holder.chkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedFiles.contains(file)) selectedFiles.add(file);
                } else {
                    selectedFiles.remove(file);
                }
                if (onFileClickListener != null) {
                    onFileClickListener.onSelectionChanged(selectedFiles.size());
                }
            });

            // ================= ITEM CLICK =================

            holder.itemView.setOnClickListener(v -> {
                if (onFileClickListener != null) {
                    onFileClickListener.onFileClicked(file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return fileList.size();
        }

        public List<FileModel> getSelectedFiles() {
            return new ArrayList<>(selectedFiles);
        }

        // ================= VIEW HOLDER =================

        static class ViewHolder extends RecyclerView.ViewHolder {

            TextView txtName, txtMeta, txtUploadState;
            CheckBox chkSelect;
            ImageView imgPreview;   // ✅ use preview, not icon
            Chip chipPriority;
            ProgressBar progressFile;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);

                txtName = itemView.findViewById(R.id.txtFileName);
                txtMeta = itemView.findViewById(R.id.txtMeta);
                txtUploadState = itemView.findViewById(R.id.txtUploadState);
                chkSelect = itemView.findViewById(R.id.chkSelect);
                imgPreview = itemView.findViewById(R.id.imgPreview); // ✅ must match XML
                chipPriority = itemView.findViewById(R.id.chipPriority);
                progressFile = itemView.findViewById(R.id.progressFile);
            }
        }

        // ================= FILE SIZE =================

        private String readableFileSize(long size) {
            if (size <= 0) return "0 B";
            final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
        }
    }
