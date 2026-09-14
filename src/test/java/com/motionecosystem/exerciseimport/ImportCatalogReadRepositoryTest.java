package com.motionecosystem.exerciseimport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportCatalogReadRepositoryTest {

    @Test
    void canonicalNameMaterializesOneResultInsteadOfLeavingAResultStreamOpen() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<String> query = mock(TypedQuery.class);
        UUID exerciseId = UUID.randomUUID();
        when(entityManager.createQuery("select exercise.canonicalName from Exercise exercise where exercise.id = :id", String.class))
                .thenReturn(query);
        when(query.setParameter("id", exerciseId)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("Canonical name"));

        String canonicalName = new ImportCatalogReadRepository(entityManager).canonicalName(exerciseId);

        assertThat(canonicalName).isEqualTo("Canonical name");
        verify(query).setMaxResults(1);
        verify(query, never()).getResultStream();
    }

    @Test
    void publishedAnatomyIdMaterializesOneResultInsteadOfLeavingAResultStreamOpen() {
        EntityManager entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<UUID> query = mock(TypedQuery.class);
        UUID anatomyId = UUID.randomUUID();
        when(entityManager.createQuery("select item.id from AnatomicalStructureJpaEntity item where item.code = :code and item.status = com.motionecosystem.anatomyreference.domain.PublicationStatus.PUBLISHED", UUID.class))
                .thenReturn(query);
        when(query.setParameter("code", "CHEST")).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(anatomyId));

        UUID publishedAnatomyId = new ImportCatalogReadRepository(entityManager).publishedAnatomyId("CHEST");

        assertThat(publishedAnatomyId).isEqualTo(anatomyId);
        verify(query).setMaxResults(1);
        verify(query).getResultList();
        verify(query, never()).getResultStream();
    }
}
