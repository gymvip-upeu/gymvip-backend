package com.gymvip.training;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training")
public class TrainingController {

    private final TrainingRepository trainingRepository;

    public TrainingController(TrainingRepository trainingRepository) {
        this.trainingRepository = trainingRepository;
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<Map<String, Object>>> exercises(
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(trainingRepository.listExercises(muscleGroup, q));
    }

    @GetMapping("/routines")
    public ResponseEntity<List<Map<String, Object>>> routines(Authentication auth) {
        return ResponseEntity.ok(trainingRepository.listRoutinesForAccount(auth.getName()));
    }

    public record CreateRoutineRequest(
            String title,
            String goal,
            String level,
            int durationMinutes,
            boolean personal,
            UUID assignedClientId,
            String notes,
            List<Map<String, Object>> items
    ) {}

    @PostMapping("/routines")
    @PreAuthorize("hasAuthority('training.routines.create')")
    public ResponseEntity<Map<String, Object>> createRoutine(
            @RequestBody CreateRoutineRequest request,
            Authentication auth
    ) {
        if (request.assignedClientId() != null && !hasCoachAuthority(auth)) {
            throw new org.springframework.security.access.AccessDeniedException("Solo un entrenador o administrador autorizado puede asignar rutinas a socios.");
        }
        boolean isPersonal = request.assignedClientId() == null && (request.personal() || !hasCoachAuthority(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                trainingRepository.createRoutine(
                        auth.getName(),
                        request.title(),
                        request.goal(),
                        request.level(),
                        request.durationMinutes(),
                        isPersonal,
                        request.assignedClientId(),
                        request.notes(),
                        request.items()
                )
        );
    }

    public record RecordWorkoutSessionRequest(
            UUID routineId,
            String routineTitle,
            int durationSeconds,
            String notes,
            String clientSyncId,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<Map<String, Object>> sets
    ) {}

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('training.sessions.log')")
    public ResponseEntity<Map<String, Object>> recordSession(
            @RequestBody RecordWorkoutSessionRequest request,
            Authentication auth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                trainingRepository.recordWorkoutSessionIdempotent(
                        auth.getName(),
                        request.routineId(),
                        request.routineTitle(),
                        request.durationSeconds(),
                        request.notes(),
                        request.clientSyncId(),
                        request.startedAt(),
                        request.completedAt(),
                        request.sets()
                )
        );
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAuthority('training.progress.read')")
    public ResponseEntity<Map<String, Object>> progress(Authentication auth) {
        return ResponseEntity.ok(trainingRepository.progressSummary(auth.getName()));
    }

    @GetMapping("/classes")
    public ResponseEntity<List<Map<String, Object>>> listClasses(Authentication auth) {
        return ResponseEntity.ok(trainingRepository.listClasses(auth.getName()));
    }

    @PostMapping("/classes/{classId}/reserve")
    @PreAuthorize("hasAuthority('training.classes.reserve')")
    public ResponseEntity<Map<String, Object>> reserveClass(@PathVariable UUID classId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainingRepository.reserveClass(auth.getName(), classId));
    }

    @PostMapping("/classes/{classId}/cancel")
    @PreAuthorize("hasAuthority('training.classes.reserve')")
    public ResponseEntity<Map<String, Object>> cancelClass(@PathVariable UUID classId, Authentication auth) {
        return ResponseEntity.ok(trainingRepository.cancelReservation(auth.getName(), classId));
    }

    private boolean hasCoachAuthority(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("training.routines.assign"));
    }
}
