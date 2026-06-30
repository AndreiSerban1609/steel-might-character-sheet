package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<GameCharacter, String> {
}
