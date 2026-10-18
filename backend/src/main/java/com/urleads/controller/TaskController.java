package com.urleads.controller;

import com.urleads.dto.CreateTaskRequest;
import com.urleads.dto.PageResponse;
import com.urleads.dto.TaskResponse;
import com.urleads.dto.UpdateTaskRequest;
import com.urleads.security.CurrentUserProvider;
import com.urleads.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final CurrentUserProvider currentUserProvider;

    public TaskController(TaskService taskService, CurrentUserProvider currentUserProvider) {
        this.taskService = taskService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        TaskResponse response = taskService.crearTarea(usuarioId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> listTasks(
            @RequestParam(required = false) Long leadId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int validatedSize = Math.min(Math.max(1, size), 100);
        int validatedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(validatedPage, validatedSize);

        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(PageResponse.from(taskService.obtenerTareas(usuarioId, leadId, estado, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(taskService.obtenerTareaPorId(id, usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String token
    ) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(taskService.actualizarTarea(id, usuarioId, request, token));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(taskService.marcarComoCompletada(id, usuarioId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        Long usuarioId = currentUserProvider.getCurrentUserId();
        taskService.eliminarTarea(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
