package com.nicolas.servicovoluntario.aplicacao;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.nicolas.servicovoluntario.regras.Violacao;

/** Falha prevista da API; o handler a converte em ProblemDetail com a mensagem em {@code detail}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<Violacao> violacoes;

    private ApiException(HttpStatus status, String mensagem, List<Violacao> violacoes) {
        super(mensagem);
        this.status = status;
        this.violacoes = List.copyOf(violacoes);
    }

    public static ApiException requisicaoInvalida(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensagem, List.of());
    }

    public static ApiException proibido(String mensagem) {
        return new ApiException(HttpStatus.FORBIDDEN, mensagem, List.of());
    }

    public static ApiException naoEncontrado(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, mensagem, List.of());
    }

    /** O pedido é válido, mas o estado atual do recurso não o permite. */
    public static ApiException conflito(String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, mensagem, List.of());
    }

    /** O pedido é válido, mas fere regras de negócio. */
    public static ApiException regrasVioladas(String mensagem, List<Violacao> violacoes) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem, violacoes);
    }

    public HttpStatus status() {
        return status;
    }

    public List<Violacao> violacoes() {
        return violacoes;
    }
}
