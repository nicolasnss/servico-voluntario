package com.nicolas.servicovoluntario.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.nicolas.servicovoluntario.aplicacao.ApiException;

/** Erros como ProblemDetail; o front exibe o campo {@code detail}. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail aoFalharRegra(ApiException e) {
        var problema = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        if (!e.violacoes().isEmpty()) {
            problema.setProperty("violacoes", e.violacoes());
        }
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail aoValidar(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos na requisição");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail aoLer(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisição malformada");
    }
}
