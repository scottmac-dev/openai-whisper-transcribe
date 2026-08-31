package com.comp3011.assignment1.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.comp3011.assignment1.responses.ErrorResponse;

/**
 * Renders every failure as the ErrorResponse schema from the YAML spec.
 * Messages are written here, never taken from the exception, so no internal detail leaks.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Catch all internal exception and funnels through to get custom body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        return build(ex, status, messageFor(status), headers, request);
    }

    /** Malformed multipart body. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        return build(ex, HttpStatus.BAD_REQUEST,
                "The audio upload was malformed or incomplete.", null, request);
    }

    /** Upstream did not answer in time, or could not be reached. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Object> handleUpstreamTimeout(ResourceAccessException ex, WebRequest request) {
        return build(ex, HttpStatus.GATEWAY_TIMEOUT,
                "Transcription provider did not respond in time.", null, request);
    }

    /** Upstream answered with an error status. */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Object> handleUpstreamError(RestClientResponseException ex, WebRequest request) {
        return build(ex, HttpStatus.BAD_GATEWAY,
                "Transcription provider is unavailable.", null, request);
    }

    /** Backstop so nothing escapes as a Spring default body. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.", null, request);
    }

    /** Builds the body and logs it for traceable debugging. */
    private ResponseEntity<Object> build(Exception ex, HttpStatusCode status, String message,
            HttpHeaders headers, WebRequest request) {

        String path = pathOf(request);
        if (status.is5xxServerError()) {
            log.error("{} on {} - responding {}", ex.getClass().getSimpleName(), path, status.value(), ex);
        } else {
            log.debug("{} on {} - responding {}", ex.getClass().getSimpleName(), path, status.value());
        }

        HttpStatus resolved = HttpStatus.resolve(status.value());
        ErrorResponse body = ErrorResponse.of(
                resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved, message, path);

        return ResponseEntity.status(status)
                .headers(headers == null ? new HttpHeaders() : headers)
                .body(body);
    }

    /** The request path, for the schema's `path` field. */
    private String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false).replaceFirst("^uri=", "");
    }

    /** Wording per status, generic on purpose. */
    private String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request was malformed or incomplete.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "That method is not supported for this endpoint.";
            case 413 -> "Audio file exceeds the 25 MB upload limit.";
            case 415 -> "The request media type is not supported by this endpoint.";
            case 502 -> "Transcription provider is unavailable.";
            case 504 -> "Transcription provider did not respond in time.";
            default -> "An unexpected server error occurred.";
        };
    }
}
