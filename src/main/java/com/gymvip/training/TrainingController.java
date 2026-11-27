package com.gymvip.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
    private final ClassReservationsService classes;
    private final ExerciseCatalogService exerciseCatalog;

    public TrainingController(TrainingRepository trainingRepository, ClassReservationsService classes, ExerciseCatalogService exerciseCatalog) {
        this.trainingRepository = trainingRepository;
        this.classes = classes;
        this.exerciseCatalog = exerciseCatalog;
    }

    @GetMapping("/exercises")
    @PreAuthorize("hasAuthority('training.routines.read')")
    public List<ExerciseCatalogService.Exercise> exercises(
            @RequestParam(required = false) String muscleGroup, @RequestParam(required = false) String q, Authentication auth
    ) {
        return exerciseCatalog.published(auth.getName(), muscleGroup, q);
    }

    @GetMapping("/exercises/{id}")
    @PreAuthorize("hasAuthority('training.routines.read')")
    public ExerciseCatalogService.Exercise exercise(@PathVariable UUID id, Authentication auth) {
        return exerciseCatalog.detail(auth.getName(), id);
    }

    @GetMapping("/exercises/manage")
    @PreAuthorize("hasAnyAuthority('training.exercises.edit','training.exercises.publish')")
    public List<ExerciseCatalogService.ManageItem> manageExercises(@RequestParam(required = false) UUID branchId, Authentication auth) {
        return exerciseCatalog.manage(auth.getName(), branchId);
    }

    @PostMapping("/exercises")
    @PreAuthorize("hasAuthority('training.exercises.edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> createExercise(@RequestParam(required = false) UUID branchId,
            @Valid @RequestBody ExerciseCatalogService.Content draft, Authentication auth) {
        return Map.of("id", exerciseCatalog.saveDraft(auth.getName(), null, branchId, draft));
    }

    @PutMapping("/exercises/{id}/draft")
    @PreAuthorize("hasAuthority('training.exercises.edit')")
    public Map<String, UUID> editExercise(@PathVariable UUID id, @Valid @RequestBody ExerciseCatalogService.Content draft, Authentication auth) {
        return Map.of("id", exerciseCatalog.saveDraft(auth.getName(), id, null, draft));
    }

    @PostMapping("/exercises/{id}/publish")
    @PreAuthorize("hasAuthority('training.exercises.publish')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publishExercise(@PathVariable UUID id, Authentication auth) {
        exerciseCatalog.publish(auth.getName(), id);
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
    @PreAuthorize("hasAnyAuthority('training.classes.reserve','training.classes.roster','training.classes.manage')")
    public List<ClassReservationsService.ClassItem> listClasses(Authentication auth) {
        return classes.list(auth.getName());
    }

    @GetMapping("/classes/trainers")
    @PreAuthorize("hasAuthority('training.classes.manage')")
    public List<ClassReservationsService.Trainer> trainers(@RequestParam(required = false) UUID branchId, Authentication auth) {
        return classes.trainers(auth.getName(), branchId);
    }

    @GetMapping("/classes/{classId}/reservations")
    @PreAuthorize("hasAuthority('training.classes.roster')")
    public List<ClassReservationsService.ReservationItem> roster(@PathVariable UUID classId, Authentication auth) {
        return classes.roster(auth.getName(), classId);
    }

    @GetMapping("/classes/notifications")
    @PreAuthorize("hasAuthority('training.classes.notifications')")
    public List<ClassReservationsService.ClassNotice> reservationNotifications(Authentication auth) {
        return classes.notices(auth.getName());
    }

    public record CreateClassRequest(UUID branchId, @NotBlank @Size(max = 140) String title,
            @NotBlank @Size(max = 60) String category, UUID trainerAccountId, @Size(max = 120) String trainerName,
            @NotNull LocalDateTime startsAt, @Min(15) @Max(480) int durationMinutes, @Min(1) @Max(1000) int capacity) {}

    @PostMapping("/classes")
    @PreAuthorize("hasAuthority('training.classes.manage')")
    public ResponseEntity<Map<String, Object>> createClass(@Valid @RequestBody CreateClassRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(classes.create(auth.getName(), request.branchId(),
                request.title(), request.category(), request.trainerAccountId(), request.trainerName(),
                request.startsAt(), request.durationMinutes(), request.capacity()));
    }

    @PostMapping("/classes/{classId}/reserve")
    @PreAuthorize("hasAuthority('training.classes.reserve')")
    public ResponseEntity<Map<String, Object>> reserveClass(@PathVariable UUID classId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(classes.reserve(auth.getName(), classId));
    }

    @PostMapping("/classes/{classId}/cancel")
    @PreAuthorize("hasAuthority('training.classes.reserve')")
    public Map<String, Object> cancelClass(@PathVariable UUID classId, Authentication auth) {
        return classes.cancel(auth.getName(), classId);
    }

    private boolean hasCoachAuthority(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("training.routines.assign"));
    }
}
