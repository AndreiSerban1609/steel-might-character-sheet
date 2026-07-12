package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.RoomEncounter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEncounterRepository extends JpaRepository<RoomEncounter, String> {
}
