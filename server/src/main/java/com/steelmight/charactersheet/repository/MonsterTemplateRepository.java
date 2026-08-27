package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.MonsterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonsterTemplateRepository extends JpaRepository<MonsterTemplate, Long> {

    List<MonsterTemplate> findByRoomNameOrderByNameAsc(String roomName);

    Optional<MonsterTemplate> findByIdAndRoomName(Long id, String roomName);
}
