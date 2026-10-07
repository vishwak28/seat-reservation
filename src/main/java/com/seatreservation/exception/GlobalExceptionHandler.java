package com.seatreservation.exception;

import com.seatreservation.metrics.ReservationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ReservationMetrics metrics;

    public GlobalExceptionHandler(ReservationMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(ReservationDeclinedException.class)
    public ProblemDetail handleDeclined(ReservationDeclinedException ex) {
        String reason = ex.getDeclineReason().name().toLowerCase(Locale.ROOT);
        metrics.declined(reason);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getReason());
        problem.setProperty("reason", reason);
        return problem;
    }

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> handleDatabaseTrouble(Exception ex) {
        log.warn("Database unavailable or contended: {}", ex.toString());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unable to process the request, please retry");
        problem.setProperty("reason", "database_unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        problem.setProperty("reason", "internal_error");
        return problem;
    }
}