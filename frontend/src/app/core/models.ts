export type Perfil = 'POLICIAL' | 'RESP_CAPTACAO' | 'GESTOR_UNIDADE' | 'COORD_CENTRAL' | 'COMANDO' | 'CONTROLE';

export interface UsuarioLogado {
  nome: string;
  matricula: string;
  perfis: Perfil[];
}

export type TipoFuncao = 'EXECUCAO' | 'SUPERVISAO';

export type StatusServico =
  | 'RASCUNHO'
  | 'CAPTACAO_ABERTA'
  | 'CAPTACAO_ENCERRADA'
  | 'SELECIONADO'
  | 'REALIZADO'
  | 'FECHADO_PARA_PAGAMENTO'
  | 'CANCELADO';

export interface Violacao {
  codigo: string;
  mensagem: string;
}

/** Resultado do AvaliadorElegibilidade do backend para o policial logado. */
export interface Elegibilidade {
  elegivel: boolean;
  violacoes: Violacao[];
}

export interface VagaServico {
  id: number;
  funcao: string;
  tipoFuncao: TipoFuncao;
  quantidade: number;
}

export interface ServicoResumo {
  id: number;
  ordemServico: string | null;
  titulo: string;
  local: string;
  unidade: string;
  inicio: string;
  fim: string;
  horasContadas: number;
  cotas: number;
  valor: number;
  captacaoFechaEm: string | null;
  status: StatusServico;
  vagas: VagaServico[];
  elegibilidade: Elegibilidade;
  jaCandidatou: boolean;
}

export type StatusCandidatura = 'CANDIDATA' | 'SELECIONADA' | 'NAO_SELECIONADA' | 'CANCELADA';

export type SituacaoExecucao = 'PRESENTE' | 'FALTOU' | 'FALTA_JUSTIFICADA';

export interface CandidaturaResumo {
  id: number;
  servicoId: number;
  titulo: string;
  local: string;
  inicio: string;
  fim: string;
  horasContadas: number;
  cotas: number;
  valor: number;
  funcao: string;
  status: StatusCandidatura;
  posicaoRanking: number | null;
  justificativa: string | null;
  situacaoExecucao: SituacaoExecucao | null;
}

/** Saldos do mês: planejado serve para checar limites, realizado serve para pagamento. */
export interface CotaMensal {
  anoMes: string;
  planejadas: number;
  realizadas: number;
  tetoIndividual: number | null;
  valorPrevisto: number;
}

export interface NovaCandidatura {
  vagaId: number;
  declaracaoArt5Aceita: boolean;
}

/** Visão do responsável pela captação: o serviço com os números da captação. */
export interface ServicoCaptacao {
  id: number;
  ordemServico: string | null;
  titulo: string;
  local: string;
  inicio: string;
  fim: string;
  horasContadas: number;
  cotas: number;
  captacaoFechaEm: string | null;
  status: StatusServico;
  vagas: VagaServico[];
  totalCandidatos: number;
  totalSelecionados: number;
}

/** Candidato já posicionado pelo ranking do backend (menos SVG primeiro, dentro de cada vaga). */
export interface CandidatoCaptacao {
  candidaturaId: number;
  vagaId: number;
  matricula: string;
  nome: string;
  postoGraduacao: string;
  cotasMesAnterior: number;
  cotasMesAtual: number;
  dataAdmissao: string;
  posicao: number;
  status: StatusCandidatura;
  situacaoExecucao: SituacaoExecucao | null;
}

export interface SelecaoCaptacao {
  candidaturaIds: number[];
  /** Obrigatória quando a seleção diverge do ranking. */
  justificativa: string | null;
}
