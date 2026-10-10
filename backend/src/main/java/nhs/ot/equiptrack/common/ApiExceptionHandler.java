package nhs.ot.equiptrack.common;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Every error leaves the API as plain, human-readable text: the frontend shows
// the response body as-is. Stack traces and internals are never returned.
@RestControllerAdvice
public class ApiExceptionHandler {

	public static final String CONCURRENT_UPDATE = "This item was just updated by someone else. Check its current state and try again.";

	public static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<String> api(ApiException e) {
		return text(e.status(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<String> invalid(MethodArgumentNotValidException e) {
		var error = e.getBindingResult().getAllErrors().stream().findFirst();
		return text(HttpStatus.BAD_REQUEST, error.map(err -> err.getDefaultMessage()).orElse("The request is not valid"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<String> unreadable(HttpMessageNotReadableException e) {
		return text(HttpStatus.BAD_REQUEST, "The request is not valid");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<String> badParameter(MethodArgumentTypeMismatchException e) {
		return text(HttpStatus.BAD_REQUEST, "Invalid value for " + e.getName());
	}

	@ExceptionHandler({ ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class })
	ResponseEntity<String> concurrentUpdate(ConcurrencyFailureException e) {
		return text(HttpStatus.CONFLICT, CONCURRENT_UPDATE);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<String> integrity(DataIntegrityViolationException e) {
		log.warn("Constraint violation: {}", e.getMostSpecificCause().getMessage());
		return text(HttpStatus.CONFLICT, "That change conflicts with existing data. Refresh and try again.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<String> denied(AccessDeniedException e) {
		return text(HttpStatus.FORBIDDEN, "You don't have permission to do that");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<String> noRoute(NoResourceFoundException e) {
		return text(HttpStatus.NOT_FOUND, "Not found");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<String> method(HttpRequestMethodNotSupportedException e) {
		return text(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<String> unexpected(Exception e) {
		log.error("Unhandled error", e);
		return text(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
	}

	public static ResponseEntity<String> text(HttpStatus status, String message) {
		return ResponseEntity.status(status).contentType(TEXT_UTF8).body(message);
	}

}
