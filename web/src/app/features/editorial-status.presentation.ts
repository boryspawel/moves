const EDITORIAL_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Szkic',
  IN_REVIEW: 'W recenzji',
  CHANGES_REQUESTED: 'Wymaga zmian',
  APPROVED: 'Zatwierdzona',
  PUBLISHED: 'Opublikowana',
  WITHDRAWN: 'Wycofana',
};

export function editorialStatusLabel(status?: string): string {
  return status ? EDITORIAL_STATUS_LABELS[status] ?? 'Nieznany stan' : 'Nieznany stan';
}
