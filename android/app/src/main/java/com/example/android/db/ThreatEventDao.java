package com.example.android.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.android.models.ThreatEvent;

import java.util.List;

@Dao
public interface ThreatEventDao {

    @Insert
    void insert(ThreatEvent event);

    @Insert
    void insertAll(List<ThreatEvent> events);

    @Query("SELECT * FROM threat_events ORDER BY timestamp DESC")
    List<ThreatEvent> getAllEvents();
}
