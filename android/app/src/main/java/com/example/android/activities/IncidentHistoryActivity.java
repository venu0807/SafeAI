package com.example.android.activities;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.R;
import com.example.android.utils.EncryptionHelper;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IncidentHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyView;
    private List<File> incidentFiles;
    private EncryptionHelper encryptionHelper;
    private volatile boolean isPlaying = false;
    private String currentlyPlayingPath = "";

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private java.util.concurrent.ExecutorService playbackExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_history);

        setupToolbar();
        encryptionHelper = new EncryptionHelper();
        recyclerView = findViewById(R.id.rv_incidents);
        emptyView = findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadIncidentFiles();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Incident Vault");
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (!isFinishing()) finish();
        });
    }

    private void loadIncidentFiles() {
        File folder = getFilesDir();
        File[] files = folder.listFiles((dir, name) -> name.startsWith("incident_") && name.endsWith(".enc"));
        
        if (files == null || files.length == 0) {
            incidentFiles = new ArrayList<>();
            emptyView.setVisibility(View.VISIBLE);
        } else {
            incidentFiles = new ArrayList<>(Arrays.asList(files));
            Collections.sort(incidentFiles, (f1, f2) -> f2.getName().compareTo(f1.getName()));
            emptyView.setVisibility(View.GONE);
        }
        recyclerView.setAdapter(new IncidentAdapter());
    }

    private void playEncryptedFile(File file) {
        if (isPlaying) {
            isPlaying = false; // Toggle off if already playing
            return;
        }

        isPlaying = true;
        currentlyPlayingPath = file.getAbsolutePath();
        recyclerView.getAdapter().notifyDataSetChanged();

        playbackExecutor.execute(() -> {
            AudioTrack audioTrack = null;
            try {
                int sampleRate = 16000;
                int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
                audioTrack.play();

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] sizeBytes = new byte[4];
                    while (isPlaying && fis.read(sizeBytes) != -1) {
                        int chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (chunkSize <= 0 || chunkSize > 1024 * 1024) break; // Sanity check

                        byte[] encryptedChunk = new byte[chunkSize];
                        int read = fis.read(encryptedChunk);
                        if (read != chunkSize) break;
                        byte[] decrypted = encryptionHelper.decrypt(encryptedChunk);
                        audioTrack.write(decrypted, 0, decrypted.length);
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show());
            } finally {
                if (audioTrack != null) {
                    audioTrack.stop();
                    audioTrack.release();
                }
                isPlaying = false;
                currentlyPlayingPath = "";
                runOnUiThread(() -> {
                    if (recyclerView.getAdapter() != null) {
                        recyclerView.getAdapter().notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.ViewHolder> {
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_incident, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = incidentFiles.get(position);
            long timestamp = 0;
            try {
                timestamp = Long.parseLong(file.getName().replace("incident_", "").replace(".enc", ""));
            } catch (Exception ignored) {}

            synchronized (DATE_FORMAT) {
                holder.dateText.setText(DATE_FORMAT.format(new Date(timestamp)));
            }
            synchronized (TIME_FORMAT) {
                holder.timeText.setText(TIME_FORMAT.format(new Date(timestamp)));
            }
            
            boolean isThisPlaying = currentlyPlayingPath.equals(file.getAbsolutePath());
            holder.playBtn.setImageResource(isThisPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            holder.playBtn.setOnClickListener(v -> playEncryptedFile(file));
            
            holder.itemView.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(IncidentHistoryActivity.this)
                        .setTitle("Delete Incident?")
                        .setPositiveButton("Delete", (d, w) -> {
                            file.delete();
                            loadIncidentFiles();
                        }).setNegativeButton("Cancel", null).show();
                return true;
            });
        }

        @Override public int getItemCount() { return incidentFiles.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dateText, timeText;
            ImageButton playBtn;
            ViewHolder(View itemView) {
                super(itemView);
                dateText = itemView.findViewById(R.id.incident_date);
                timeText = itemView.findViewById(R.id.incident_time);
                playBtn = itemView.findViewById(R.id.btn_play);
            }
        }
    }
}
