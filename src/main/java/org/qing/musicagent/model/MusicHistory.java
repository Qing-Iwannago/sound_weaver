package org.qing.musicagent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "music_history")
public class MusicHistory {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String username;

        private String description;
        private String mood;
        private String genre;
        private int bpm;

        @Column(name = "music_key")
        private String key;

        @Column(columnDefinition = "TEXT")
        private String chords;

        @Column(columnDefinition = "TEXT")
        private String lyrics;

        private String filePath;
        private LocalDateTime createdAt;

        @PrePersist
        public void prePersist() {
                this.createdAt = LocalDateTime.now();
        }
}