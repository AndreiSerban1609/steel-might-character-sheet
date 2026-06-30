package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.service.DeckTemplateService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final DeckTemplateService deckService;

    public RoomController(DeckTemplateService deckService) {
        this.deckService = deckService;
    }

    @GetMapping("/{room}/deck")
    public DeckTemplate getDeck(@PathVariable String room) {
        return deckService.getTemplate(room);
    }

    @PutMapping("/{room}/deck")
    public DeckTemplate updateDeck(@PathVariable String room, @RequestBody DeckTemplate template) {
        return deckService.updateTemplate(room, template);
    }
}
