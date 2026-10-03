package pl.aniolstroz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** All HTTP errors leave the backend as RFC 9457 ProblemDetail with a Polish {@code detail} for the UI. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        // Log the type only: messages may carry request data.
        log.error("Unhandled exception: {}", ex.getClass().getName());
        ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Spring passes a null body for ErrorResponse exceptions and resolves it from the exception later.
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem) {
            problem.setDetail(polishDetail(statusCode));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static String polishDetail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Nieprawidłowe żądanie.";
            case 404 -> "Nie znaleziono zasobu.";
            case 405 -> "Ta metoda nie jest obsługiwana.";
            case 406 -> "Nieobsługiwany format odpowiedzi.";
            case 415 -> "Nieobsługiwany format danych.";
            default -> status.is4xxClientError()
                    ? "Nie można obsłużyć żądania."
                    : "Wystąpił błąd po stronie serwera. Spróbuj ponownie później.";
        };
    }
}
