package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateView;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.service.MonsterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Room-scoped monster library (templates) and the monsters currently in the fight (instances). */
@RestController
@RequestMapping("/api/rooms/{room}")
public class MonsterController {

    private final MonsterService monsters;

    public MonsterController(MonsterService monsters) {
        this.monsters = monsters;
    }

    // ---- Templates ----

    /** Doubles as EXPORT: each entry minus id/roomName is a valid import request (E9). */
    @GetMapping("/monster-templates")
    public List<MonsterTemplateView> listTemplates(@PathVariable String room) {
        return monsters.listTemplates(room);
    }

    @PostMapping("/monster-templates")
    public ResponseEntity<MonsterTemplateView> createTemplate(@PathVariable String room,
                                                              @Valid @RequestBody MonsterTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsters.createTemplate(room, req));
    }

    @PostMapping("/monster-templates/import")
    public ResponseEntity<List<MonsterTemplateView>> importTemplates(@PathVariable String room,
                                                                     @RequestBody List<@Valid MonsterTemplateRequest> reqs) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsters.importTemplates(room, reqs));
    }

    /** Story 2.5: a full-resource mirror of a character, ready to spawn for their Death fight. */
    @PostMapping("/monster-templates/from-character/{playerId}")
    public ResponseEntity<MonsterTemplateView> templateFromCharacter(@PathVariable String room,
                                                                     @PathVariable String playerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsters.templateFromCharacter(room, playerId));
    }

    @PutMapping("/monster-templates/{id}")
    public MonsterTemplateView updateTemplate(@PathVariable String room, @PathVariable Long id,
                                              @Valid @RequestBody MonsterTemplateRequest req) {
        return monsters.updateTemplate(room, id, req);
    }

    @DeleteMapping("/monster-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String room, @PathVariable Long id) {
        monsters.deleteTemplate(room, id);
        return ResponseEntity.noContent().build();
    }

    // ---- Instances ----

    @GetMapping("/monsters")
    public List<MonsterView> list(@PathVariable String room) {
        return monsters.list(room);
    }

    @GetMapping("/monsters/{id}")
    public MonsterView get(@PathVariable String room, @PathVariable Long id) {
        return monsters.get(room, id);
    }

    /** Returns only the monsters spawned by this call. */
    @PostMapping("/monsters")
    public ResponseEntity<List<MonsterView>> spawn(@PathVariable String room,
                                                   @Valid @RequestBody SpawnMonstersRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monsters.spawn(room, req));
    }

    @DeleteMapping("/monsters/{id}")
    public ResponseEntity<Void> delete(@PathVariable String room, @PathVariable Long id) {
        monsters.delete(room, id);
        return ResponseEntity.noContent().build();
    }

    /** Clear the fight: every monster in the room. */
    @DeleteMapping("/monsters")
    public ResponseEntity<Void> clear(@PathVariable String room) {
        monsters.clear(room);
        return ResponseEntity.noContent().build();
    }
}
