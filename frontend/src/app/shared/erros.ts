import { HttpErrorResponse } from '@angular/common/http';

/** Extrai a mensagem de um erro de API (ProblemDetail do Spring) ou de um erro comum. */
export function mensagemDeErro(erro: unknown): string {
  if (erro instanceof HttpErrorResponse) {
    const corpo = erro.error as { detail?: string; mensagem?: string } | null;
    return corpo?.detail ?? corpo?.mensagem ?? 'Não foi possível concluir a operação. Tente novamente.';
  }
  if (erro instanceof Error) {
    return erro.message;
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}
