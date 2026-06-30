package com.steelmight.charactersheet.repository;

import com.steelmight.charactersheet.model.RoomDeck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomDeckRepository extends JpaRepository<RoomDeck, String> {
}
