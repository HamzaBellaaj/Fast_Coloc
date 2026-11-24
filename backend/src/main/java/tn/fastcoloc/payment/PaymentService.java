package tn.fastcoloc.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import tn.fastcoloc.api.ApiException;
import tn.fastcoloc.onboarding.OnboardingService;
import tn.fastcoloc.security.DatabaseIdentity;

@Service
public class PaymentService {
    private static final Map<Integer, BigDecimal> PRICES = Map.of(
        1, new BigDecimal("5.00"), 3, new BigDecimal("12.00"), 6, new BigDecimal("20.00"));
    private static final String TABLE = "fastcoloc.boost_payments";
    enum CheckoutMethod { PAYPAL, CARD }
    enum Status { PENDING, PAID, FAILED, CANCELLED }
    record Checkout(String reference, String url) {}
    record Result(UUID id, Status status, String approveUrl) {}
    private record Payment(UUID id, UUID actor, UUID listing, int months,
                           BigDecimal amount, Status status, String reference, String url, Instant expires) {}
    private final JdbcTemplate jdbc;
    private final OnboardingService accounts;
    private final DatabaseIdentity identity;
    private final PayPalService paypal;
    private final String frontend;

    public PaymentService(JdbcTemplate jdbc, OnboardingService accounts, DatabaseIdentity identity,
                          PayPalService paypal, @Value("${app.frontend-url}") String frontend) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.identity = identity;
        this.paypal = paypal;
        this.frontend = frontend.replaceFirst("/+$", "");
    }

    @Transactional
    public Result create(UUID actor, UUID listing, int months, CheckoutMethod method, UUID requestId) {
        accounts.bindAndLock(actor);
        BigDecimal amount = PRICES.get(months);
        if (amount == null || listing == null || requestId == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOST", "Formule de boost invalide.");
        Payment previous = find("id=? and provider='PAYPAL'", requestId);
        if (previous != null) {
            if (!previous.actor().equals(actor) || !previous.listing().equals(listing)
                    || previous.months() != months) throw missing();
            return checkout(previous);
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from fastcoloc.listings where id=? and author_id=?
              and status='PUBLISHED' and not is_hidden and expires_at>statement_timestamp())
            """, Boolean.class, listing, actor))) throw missing();

        Payment open = find("""
            actor_id=? and listing_id=? and months=? and provider='PAYPAL' and status='PENDING'
            and expires_at>statement_timestamp() order by expires_at desc limit 1
            """, actor, listing, months);
        if (open != null) return checkout(open);

        Checkout remote;
        try {
            remote = paypal.create(requestId, amount, frontend, method == CheckoutMethod.CARD);
        } catch (RestClientException e) {
            throw unavailable();
        }
        if (!remote.reference().matches("[A-Za-z0-9-]{1,128}") || !remote.url().startsWith("https://"))
            throw unavailable();

        jdbc.update("""
            insert into fastcoloc.boost_payments
                (id, actor_id, listing_id, months, provider, amount, currency, provider_ref, approval_url, expires_at)
            values (?, ?, ?, ?, 'PAYPAL', ?, 'USD', ?, ?, now() + interval '3 hours')
            """, requestId, actor, listing, months, amount, remote.reference(), remote.url());
        return new Result(requestId, Status.PENDING, remote.url());
    }

    private Result checkout(Payment payment) {
        boolean open = payment.status() == Status.PENDING && payment.expires().isAfter(Instant.now());
        return new Result(payment.id(), payment.status(), open ? payment.url() : null);
    }

    @Transactional
    public Result confirm(UUID actor, UUID id, boolean cancel) {
        accounts.bindAndLock(actor);
        Payment payment = find("id=? and actor_id=? and provider='PAYPAL' for update", id, actor);
        if (payment == null) throw missing();
        return reconcile(payment, !cancel && payment.status() == Status.PENDING, cancel);
    }

    @Transactional
    public void webhook(String reference, boolean capture) {
        if (reference == null || !reference.matches("[A-Za-z0-9-]{1,128}")) return;
        Payment payment = find("provider='PAYPAL' and provider_ref=? for update", reference);
        if (payment != null) reconcile(payment, capture && payment.status() == Status.PENDING, false);
    }

    private Result reconcile(Payment payment, boolean capture, boolean cancel) {
        Status status = payment.status();
        if (status == Status.PAID)
            return new Result(payment.id(), status, null);
        Status remote;
        try {
            remote = paypalStatus(payment, paypal.order(payment.reference(), payment.id(), capture));
        } catch (RestClientException e) {
            throw unavailable();
        }
        if (remote == Status.PAID) {
            identity.bind(payment.actor());
            int changed = jdbc.update("""
                update fastcoloc.listings set golden_until =
                    greatest(golden_until, statement_timestamp()) + (? * interval '1 month'),
                    updated_at=now() where id=? and author_id=?
                """, payment.months(), payment.listing(), payment.actor());
            if (changed != 1)
                throw new ApiException(HttpStatus.CONFLICT, "BOOST_UNAVAILABLE", "Paiement confirmé, annonce indisponible.");
        }
        if (remote != Status.PENDING || cancel && status == Status.PENDING) {
            status = remote == Status.PENDING ? Status.CANCELLED : remote;
            jdbc.update("update " + TABLE + " set status=?, updated_at=now() where id=?", status.name(), payment.id());
        }
        return new Result(payment.id(), status, null);
    }

    private Status paypalStatus(Payment payment, JsonNode order) {
        JsonNode units = order.path("purchase_units");
        JsonNode unit = units.path(0);
        requireMatch(units.size() == 1 && payment.reference().equals(order.path("id").asText())
            && payment.id().toString().equals(unit.path("custom_id").asText()));
        JsonNode captures = unit.path("payments").path("captures");
        if (captures.size() == 0)
            return "VOIDED".equals(order.path("status").asText()) ? Status.CANCELLED : Status.PENDING;
        requireMatch(captures.size() == 1);
        JsonNode capture = captures.path(0);
        return switch (capture.path("status").asText()) {
            case "COMPLETED" -> {
                JsonNode amount = capture.path("amount");
                BigDecimal value;
                try {
                    value = new BigDecimal(amount.path("value").asText());
                } catch (NumberFormatException e) {
                    throw unavailable();
                }
                requireMatch("USD".equals(amount.path("currency_code").asText()) && payment.amount().compareTo(value) == 0);
                yield Status.PAID;
            }
            case "DECLINED", "DENIED", "FAILED" -> Status.FAILED;
            default -> Status.PENDING;
        };
    }

    private Payment find(String predicate, Object... args) {
        return jdbc.query("select * from " + TABLE + " where " + predicate, (rs, row) -> new Payment(
            rs.getObject("id", UUID.class), rs.getObject("actor_id", UUID.class),
            rs.getObject("listing_id", UUID.class), rs.getInt("months"),
            rs.getBigDecimal("amount"), Status.valueOf(rs.getString("status")), rs.getString("provider_ref"),
            rs.getString("approval_url"), rs.getTimestamp("expires_at").toInstant()), args).stream().findFirst().orElse(null);
    }

    private void requireMatch(boolean matches) {
        if (!matches) throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_MISMATCH", "Données de paiement incohérentes.");
    }

    static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_UNAVAILABLE",
            "Prestataire de paiement indisponible. Veuillez réessayer.");
    }

    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Annonce ou paiement introuvable.");
    }
}
