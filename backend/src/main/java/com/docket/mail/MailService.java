package com.docket.mail;

import com.docket.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {
  private static final Logger log = LoggerFactory.getLogger(MailService.class);
  private final JavaMailSender sender;
  private final AppProperties props;

  public MailService(JavaMailSender sender, AppProperties props) { this.sender = sender; this.props = props; }

  /** Sent asynchronously so response time does not reveal whether an account exists. Never logs the token. */
  @Async
  public void sendPasswordReset(String to, String name, String rawToken, long ttlMinutes) {
    String link = props.frontendUrl().replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom(props.mailFrom());
    m.setTo(to);
    m.setSubject("Reset your DOCKET password");
    m.setText("Hi " + name + ",\n\nSomeone asked to reset the password for your DOCKET account. "
        + "Use this link within " + ttlMinutes + " minutes:\n\n" + link
        + "\n\nIf this was not you, ignore this email. Your password stays the same.\n");
    try {
      sender.send(m);
      log.info("Password reset email accepted by the mail server");
    } catch (MailException e) {
      log.error("Password reset email could not be sent ({})", e.getClass().getSimpleName());
    }
  }

  /**
   * Sends one reminder email and waits for the mail server's answer. Returns true only when the server accepted
   * the message (that is the most SMTP can confirm; it is not proof the person has read it). On failure returns the
   * exception class name only, so no address or content ends up in logs.
   */
  public String sendReminder(String to, String name, String title, String when, String path) {
    String link = props.frontendUrl().replaceAll("/+$", "") + path;
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom(props.mailFrom());
    m.setTo(to);
    m.setSubject("DOCKET reminder: " + title);
    m.setText("Hi " + name + ",\n\n" + title + "\nWhen: " + when + "\n\nOpen it in DOCKET: " + link
        + "\n\nYou receive this because email reminders are on in your DOCKET settings. You can turn them off there.\n");
    try {
      sender.send(m);
      return null;
    } catch (MailException e) {
      log.error("Reminder email could not be sent ({})", e.getClass().getSimpleName());
      return e.getClass().getSimpleName();
    }
  }

  /** Verification link to the NEW address. Sent asynchronously; never logs the token or addresses. */
  @Async
  public void sendEmailChange(String to, String name, String rawToken, long ttlMinutes) {
    String link = props.frontendUrl().replaceAll("/+$", "") + "/confirm-email?token=" + rawToken;
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom(props.mailFrom());
    m.setTo(to);
    m.setSubject("Confirm your new DOCKET email address");
    m.setText("Hi " + name + ",\n\nSomeone asked to use this address as the login email for a DOCKET account. "
        + "Confirm within " + ttlMinutes + " minutes:\n\n" + link
        + "\n\nIf this was not you, ignore this email. Nothing changes until the link is opened.\n");
    try { sender.send(m); log.info("Email-change verification accepted by the mail server"); }
    catch (MailException e) { log.error("Email-change verification could not be sent ({})", e.getClass().getSimpleName()); }
  }

  /** Heads-up to the OLD address after the login email changed. */
  @Async
  public void sendEmailChanged(String oldAddress, String name, String newAddress) {
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom(props.mailFrom());
    m.setTo(oldAddress);
    m.setSubject("Your DOCKET login email was changed");
    m.setText("Hi " + name + ",\n\nThe login email for your DOCKET account was changed to " + newAddress
        + ". If this was not you, reset your password straight away using the new address, or contact support.\n");
    try { sender.send(m); log.info("Email-changed notice accepted by the mail server"); }
    catch (MailException e) { log.error("Email-changed notice could not be sent ({})", e.getClass().getSimpleName()); }
  }
}
