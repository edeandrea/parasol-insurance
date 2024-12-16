package org.parasol.ai;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.parasol.model.Claim;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;

import dev.langchain4j.agent.tool.Tool;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@ApplicationScoped
public class NotificationService {
	// Invalid status to set
	static final String INVALID_STATUS = "Status \"%s\" is not a valid status";

	// Respond to the AI with success
	static final String NOTIFICATION_SUCCESS = "%s (claim number %s) has been notified of status update \"%s\"";

	// Respond to the AI with the fact that we couldn't find a claim record for some reason (shouldn't ever happen, but who knows...)
	static final String NOTIFICATION_NO_CLAIMANT_FOUND = "No claim record found in the database for the given claim";

	// Who the email is from
	static final String MESSAGE_FROM = "noreply@parasol.com";

	// Email subject
	static final String MESSAGE_SUBJECT = "Update to your claim";

	// Email body
	static final String MESSAGE_BODY = """
		Dear %s,
		
		This is an official communication from the Parasol Insurance Claims Department. We wanted to let you know that your claim (claim # %s) has changed status to "%s".
		
		Sincerely,
		Parasoft Insurance Claims Department
		
		--------------------------------------------
		Please note this is an unmonitored email box.
		Should you choose to reply, nobody (not even an AI bot) will see your message.
		Call a real human should you have any questions. 1-800-CAR-SAFE.
		""";

	// Using a ReactiveMailer instead of Mailer because the mailer hangs on the vert.x thread when streaming responses
	@Inject
	ReactiveMailer mailer;

	@Tool("update claim status")
	@Transactional
	@WithSpan("NotificationService.updateClaimStatus")
	public String updateClaimStatus(@SpanAttribute("arg.claimId") long claimId, @SpanAttribute("arg.status") String status) {
		Log.debugf("Updating claim status for claim %d to \"%s\"", claimId, status);

		// Only want to actually do anything if the passed in status has at lease 3 characters
		return Optional.ofNullable(status)
			.filter(s -> s.trim().length() > 2)
			.map(s -> updateStatus(claimId, s))
			.orElse(INVALID_STATUS.formatted(status));
	}

	private String updateStatus(long claimId, String status) {
		// Only want to actually do anything if there is a corresponding claim in the database for the given claimId
		return Claim.<Claim>findByIdOptional(claimId)
			.map(claim -> updateStatus(claim, status))
			.orElse(NOTIFICATION_NO_CLAIMANT_FOUND);
	}

	private String updateStatus(Claim claim, String status) {
		// Capitalize the first letter
		claim.status = status.trim().substring(0, 1).toUpperCase() + status.trim().substring(1);

		// Save the claim with updated status
		Claim.persist(claim);

		// Send the email
		sendEmail(claim);

		// Return a note to the AI
		return NOTIFICATION_SUCCESS.formatted(claim.emailAddress, claim.claimNumber, claim.status);
	}

	private void sendEmail(Claim claim) {
		// Create the email
		var email = Mail.withText(
			claim.emailAddress,
				MESSAGE_SUBJECT,
				MESSAGE_BODY.formatted(claim.clientName, claim.claimNumber, claim.status)
			)
			.setFrom(MESSAGE_FROM);

		// Send the email to the user
		// Need to move this to another thread because the mailer blocks the vert.x thread
		// while polling the SMTP connection, which causes deadlock.
		// Fail if it doesn't finish in 15 seconds
		this.mailer.send(email)
			.runSubscriptionOn(ForkJoinPool.commonPool())
			.await().atMost(Duration.ofSeconds(15));
	}
}
