import { SituacaoExecucao, StatusCandidatura, StatusServico } from '../core/models';

export interface Rotulo {
  texto: string;
  classe: '' | 'selo--ok' | 'selo--alerta' | 'selo--erro';
}

export const ROTULO_STATUS_SERVICO: Record<StatusServico, Rotulo> = {
  RASCUNHO: { texto: 'Rascunho', classe: '' },
  CAPTACAO_ABERTA: { texto: 'Captação aberta', classe: 'selo--ok' },
  CAPTACAO_ENCERRADA: { texto: 'Captação encerrada', classe: 'selo--alerta' },
  SELECIONADO: { texto: 'Seleção concluída', classe: '' },
  REALIZADO: { texto: 'Realizado', classe: '' },
  FECHADO_PARA_PAGAMENTO: { texto: 'Fechado para pagamento', classe: '' },
  CANCELADO: { texto: 'Cancelado', classe: 'selo--erro' },
};

export const ROTULO_STATUS_CANDIDATURA: Record<StatusCandidatura, Rotulo> = {
  CANDIDATA: { texto: 'Aguardando seleção', classe: 'selo--alerta' },
  SELECIONADA: { texto: 'Selecionado', classe: 'selo--ok' },
  NAO_SELECIONADA: { texto: 'Não selecionado', classe: '' },
  CANCELADA: { texto: 'Cancelada', classe: 'selo--erro' },
};

export const ROTULO_SITUACAO_EXECUCAO: Record<SituacaoExecucao, Rotulo> = {
  PRESENTE: { texto: 'Presença registrada', classe: 'selo--ok' },
  FALTOU: { texto: 'Falta registrada', classe: 'selo--erro' },
  FALTA_JUSTIFICADA: { texto: 'Falta justificada', classe: 'selo--alerta' },
};
