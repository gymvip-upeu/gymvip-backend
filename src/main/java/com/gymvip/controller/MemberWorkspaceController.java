package com.gymvip.controller;

import com.gymvip.service.MemberWorkspaceService;
import com.gymvip.service.CoverageService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/members")
@PreAuthorize("hasAnyRole('ADMIN','RECEPCION')")
public class MemberWorkspaceController {
    private final MemberWorkspaceService service;
    private final CoverageService coverage;
    public MemberWorkspaceController(MemberWorkspaceService service,CoverageService coverage) {this.service=service;this.coverage=coverage;}
    @PostMapping public ResponseEntity<MemberWorkspaceService.Registered> register(@Valid @RequestBody MemberWorkspaceService.Registration request,Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.register(request,auth.getName()));
    }
    @GetMapping("/overview") public Map<UUID,Object> overview(@RequestParam List<UUID> ids) {
        if(ids.size()>20) throw new IllegalArgumentException("Consulta hasta 20 miembros por página.");
        Map<UUID,Object> result=new LinkedHashMap<>();for(UUID id:ids) result.put(id,coverage.verify(id));return result;
    }
    @GetMapping("/{id}") public Map<String,Object> workspace(@PathVariable UUID id) {return service.workspace(id);}
    @PostMapping("/{id}/app-access") public ResponseEntity<MemberWorkspaceService.Credentials> access(@PathVariable UUID id,Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.provision(id,auth.getName()));
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasRole('ADMIN')")
    public Object status(@PathVariable UUID id,@Valid @RequestBody MemberWorkspaceService.StatusChange request,Authentication auth) {return service.status(id,request,auth.getName());}
    @PostMapping("/{id}/attendance") public Object attend(@PathVariable UUID id,Authentication auth) {return service.checkIn(id,auth.getName());}
    @PostMapping("/{id}/periods/{period}/cancel") @PreAuthorize("hasRole('ADMIN')")
    public Object cancel(@PathVariable UUID id,@PathVariable UUID period,@Valid @RequestBody MemberWorkspaceService.Cancellation request,Authentication auth) {
        service.cancel(id,period,request,auth.getName());return Map.of("cancelled",true,"periodId",period);
    }
}
