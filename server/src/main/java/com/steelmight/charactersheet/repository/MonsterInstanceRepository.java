package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.MonsterInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonsterInstanceRepository extends JpaRepository<MonsterInstance, Long> {

    List<MonsterInstance> findByRoomNameOrderByIdAsc(String roomName);

    Optional<MonsterInstance> findByIdAndRoomName(Long id, String roomName);

    long countByRoomNameAndTemplateId(String roomName, Long templateId);

    void deleteByRoomName(String roomName);
}
