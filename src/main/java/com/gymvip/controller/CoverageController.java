package com.gymvip.controller;

import com.gymvip.dto.response.CoverageResponse;
import com.gymvip.service.CoverageService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coverage")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class CoverageController {
    private final CoverageService service;
    public CoverageController(CoverageService service) {this.service=service;}
    @GetMapping("/clients/{id}")
    public CoverageResponse verify(@PathVariable UUID id) {return service.verify(id);}
    @GetMapping
    public Page<CoverageResponse> list(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="ALERTAS") String state,
            @RequestParam(required=false) UUID planId,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(query,state,planId,from,to,page,size);
    }
}
