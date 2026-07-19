package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.AuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByRoomNameOrderByIdDesc(String roomName, Pageable pageable);

    long countByRoomName(String roomName);

    List<AuditEntry> findByRoomNameOrderByIdAsc(String roomName, Pageable pageable);
}
