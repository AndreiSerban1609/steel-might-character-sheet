package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.PlayerDeck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerDeckRepository extends JpaRepository<PlayerDeck, String> {
}
