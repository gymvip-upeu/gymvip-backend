package com.gymvip.controller;
import com.gymvip.service.MemberWorkspaceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/member-app")
@PreAuthorize("hasRole('CLIENTE')")
public class MemberAppController {
    private final MemberWorkspaceService service;
    public MemberAppController(MemberWorkspaceService service) {this.service=service;}
    @GetMapping("/session") public Object session(Authentication auth) {return Map.of("mustChangePassword",service.mustChange(auth.getName()));}
    @PostMapping("/password") public Object password(@Valid @RequestBody MemberWorkspaceService.PasswordChange request,Authentication auth) {
        service.changePassword(auth.getName(),request);return Map.of("changed",true,"requiresLogin",true);
    }
    @GetMapping("/me") public Object me(Authentication auth) {
        if(service.mustChange(auth.getName())) throw new org.springframework.security.access.AccessDeniedException("Cambia la contraseña temporal antes de continuar.");
        return service.workspace(service.ownClient(auth.getName()));
    }
}
