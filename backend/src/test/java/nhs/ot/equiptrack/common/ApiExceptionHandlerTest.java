package nhs.ot.equiptrack.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ApiExceptionHandlerTest {

	@Test
	void optimisticLockConflictIsAClear409() {
		var response = new ApiExceptionHandler().concurrentUpdate(new ObjectOptimisticLockingFailureException("Equipment", 1L));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isEqualTo("This item was just updated by someone else. Check its current state and try again.");
		assertThat(response.getHeaders().getContentType()).hasToString("text/plain;charset=UTF-8");
	}

	@Test
	void unexpectedErrorsHideDetails() {
		var response = new ApiExceptionHandler().unexpected(new IllegalStateException("db password is hunter2"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isEqualTo("Something went wrong. Please try again.");
	}

}
