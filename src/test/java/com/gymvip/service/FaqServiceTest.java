package com.gymvip.service;

import com.gymvip.dto.faq.FaqDtos;
import com.gymvip.exception.ConflictException;
import com.gymvip.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaqServiceTest {
    FaqRepository repository = mock(FaqRepository.class);
    AuditService audit = mock(AuditService.class);
    FaqService service = new FaqService(repository, audit);
    FaqDtos.Content approved = new FaqDtos.Content("¿Cómo renuevo?", "En recepción.", "Renovaciones");
    FaqDtos.Content edited = new FaqDtos.Content("¿Cómo renuevo?", "Consulta los planes en recepción.", "Renovaciones");
    FaqDtos.Record published = new FaqDtos.Record(UUID.randomUUID(), approved, null, true, 2);

    @BeforeEach void setup() {
        when(repository.findAll()).thenReturn(List.of(published));
        when(repository.save(any(), any(), any(), anyBoolean())).thenAnswer(call -> {
            FaqDtos.Record previous = call.getArgument(0);
            return new FaqDtos.Record(previous.id(), call.getArgument(1), call.getArgument(2),
                call.getArgument(3), previous.revision() + 1);
        });
    }

    @Test void publishedEditRetainsApprovedContentUntilPublication() {
        service.edit(published.id(), new FaqDtos.Update(2, edited), "recepcion");
        verify(repository).save(published, approved, edited, true);
    }

    @Test void publicationPromotesOnlyTheReviewedRevision() {
        var pending = new FaqDtos.Record(published.id(), approved, edited, true, 3);
        when(repository.findAll()).thenReturn(List.of(pending));
        service.transition(pending.id(), 3, "publish", "admin");
        verify(repository).save(pending, edited, null, true);
    }

    @Test void staleRevisionCannotOverwriteNewerContent() {
        assertThrows(ConflictException.class,
            () -> service.edit(published.id(), new FaqDtos.Update(1, edited), "admin"));
        verify(repository, never()).save(any(), any(), any(), anyBoolean());
    }

    @Test void duplicateDetectionHandlesAccentsCaseAndPunctuation() {
        var duplicate = new FaqDtos.Content("COMO renuevo!!!", "Una respuesta distinta.", "Renovaciones");
        assertThrows(ConflictException.class, () -> service.create(duplicate, "admin"));
        verify(repository, never()).create(any());
    }

    @Test void deactivationPreservesPendingProposal() {
        var pending = new FaqDtos.Record(published.id(), approved, edited, true, 3);
        when(repository.findAll()).thenReturn(List.of(pending));
        service.transition(pending.id(), 3, "deactivate", "admin");
        verify(repository).save(pending, approved, edited, false);
    }
}
