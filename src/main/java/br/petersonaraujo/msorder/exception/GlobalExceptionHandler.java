package br.petersonaraujo.msorder.exception;

import br.petersonaraujo.msorder.exception.domain.EntidadeNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntidadeNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleEntidadeNaoEncontradaException(EntidadeNaoEncontradaException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse erroResposta = new ErrorResponse(
                status.value(),
                "Recurso Não Encontrado",
                ex.getMessage(),
                null
        );

        return ResponseEntity.status(status).body(erroResposta);
    }
}
